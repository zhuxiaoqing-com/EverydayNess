package org.evd.game.runtime.Db.table;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.protostuff.Schema;
import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.ProtostuffSerializer;
import org.evd.game.runtime.Db.snapshot.SnapshotValue;
import org.evd.game.runtime.Db.table.util.TimeCostPrint;
import org.evd.game.runtime.support.TwoTuple;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;


public abstract class TTable<K, V extends DirtyObject> {
    public static final Object defaultValue = new Object();
    private static final Logger logger = LoggerFactory.getLogger(TTable.class);

    Mdb mdb;
    private ProtostuffSerializer recoverySerializer;

    //private TTableCache<K, V> cache;
    private final Map<K, TRecord<K, V>> cache = new HashMap<>(500);
    Cache<Object, Object> findFailCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .executor(Runnable::run)
            .build();

    private final AtomicLong countGetMiss = new AtomicLong();
    private long allCountGetMiss = 0;


    public void checkCreateTable(CallPoint callPoint) {
        if (!dbExec(createCreateTableDBReq(), callPoint)) {
            throw new DBException("create table fail table " + getName());
        }
    }


    /*
    get add remove
    特别是get里的sync 如果选择了sync模式，add remove 就都不需要这个协程锁了吧，然后这个get的sync 由Mdb的一个参数开启 默认是关闭的;
    不然的话如果就get 有sync,其他两个没有 就会有消息顺序不一致的问题， 比如：A add 加协程锁， 然后下一个A 又get sync，不就运行的比Aadd 加协程锁的还快吗;
        所以sync不能由get传入，而是一开始就要定下来 是不是要用sync;
     */
    private boolean isSync() {
        return getMdb().isSyncGetEnabled();
    }

    public V get(K key) {
        return get(key, false);
    }

    /** 按表声明的主键类型加载玩家数据，避免 Long playerId 误传给 String/Integer 表。 */
    V getForLoad(long playerId) {
        return get(playerKey(playerId), true);
    }

    private V get(K key, boolean forLoad) {
        boolean sync = isSync();
        checkAccess(key, forLoad);
        try (ContinuationLockScope ignored = sync ? null : mdb.service.awaitCoroutineLockScope(
                LockType.TABLE_RECORD, new TwoTuple<>(getName(), key))) {
            checkAccess(key, forLoad);
            return getFromDb(key, sync, forLoad);
        }
    }

    private V getFromDb(K key, boolean sync, boolean forLoad) {
        TRecord<K, V> tRecord = cache.get(key);
        if (tRecord != null) {
            if (tRecord.isRemoveState()) {
                return null;
            }
            return tRecord.get();
        }

        if (findFailCache.getIfPresent(key) != null) {
            return null;
        }
        CallPoint callPoint = findDBServiceCallPoint(key);
        if (callPoint == null) {
            throw new DBException("DBService未连接: table=" + getName() + ", key=" + key);
        }

        V value = getExec(createGetDBReq(key), callPoint, sync);
        checkAccess(key, forLoad);

        // 查询返回后以缓存中的记录为准，不用旧数据库结果覆盖缓存状态。
        tRecord = cache.get(key);
        if (tRecord != null) {
            if (tRecord.isRemoveState()) {
                return null;
            }
            return tRecord.get();
        }
        if (value == null) {
            findFailCache.put(key, defaultValue);
            countGetMiss.incrementAndGet();
            return null;
        }

        tRecord = new TRecord<>(this, key, value, TRecord.GET, callPoint);
        cache.put(key, tRecord);
            return tRecord.getValue();
    }

    /**
     * 插入本地缓存中的新记录；数据库提交由 checkpoint 或 asyncSyncNow 完成。
     *
     * @return 本地确认 key 不存在并接纳插入时返回 true；已存在时返回 false
     */
    public boolean add(K key, V value) {
        getMdb().checkPlayerAccess(key, false);
        if (value == null) {
            throw new NullPointerException("value is null");
        }

        boolean sync = isSync();
        try (ContinuationLockScope ignored = sync ? null : mdb.service.awaitCoroutineLockScope(
                LockType.TABLE_RECORD, new TwoTuple<>(getName(), key))) {
            getMdb().checkPlayerAccess(key, false);
            TRecord<K, V> tRecord = cache.get(key);
            if (tRecord != null) {
                if (!tRecord.isRemoveState() || tRecord.isModified()
                        || !cache.remove(key, tRecord)) {
                    return false;
                }
            }

            // 预查用于尽早报告已有 key；最终保存使用 REPLACE，适用于并发写入。
            findFailCache.invalidate(key);
            getFromDb(key, sync, false);
            // 数据库里已经存在;
            if (cache.containsKey(key)) {
                return false;
            }

            value.setMemVersion(0L);
            value.setDbVersion(0L);
            value.setSnapshotVersion(0L);
            tRecord = new TRecord<>(this, key, value, TRecord.ADD,
                    findDBServiceCallPoint(key));
            cache.put(key, tRecord);
            findFailCache.invalidate(key);
            return true;
        }
    }

    /** 删除记录前先读取数据库中的完整记录，保留其数据库版本。 */
    public boolean remove(K key) {
        getMdb().checkPlayerAccess(key, false);
        boolean sync = isSync();
        try (ContinuationLockScope ignored = sync ? null : mdb.service.awaitCoroutineLockScope(
                LockType.TABLE_RECORD, new TwoTuple<>(getName(), key))) {
            getMdb().checkPlayerAccess(key, false);
            TRecord<K, V> tRecord = cache.get(key);
            if (tRecord == null) {
                findFailCache.invalidate(key);
                getFromDb(key, sync, false);
                tRecord = cache.get(key);
            }
            if (tRecord == null) {
                return true;
            }
            if (tRecord.isRemoveState()) {
                return true;
            }
            tRecord.remove();
            findFailCache.invalidate(key);
            return true;
        }
    }

    public void checkpoint(boolean close) {
        logger.info("checkpoint table Start table {} ", getName());
        if (!close && getMdb().isClosing()) {
            logger.info("checkpoint ignore because mdb is closing table {}", getName());
            return;
        }

        //todo 这里遍历当前所有的的callpoint; 然后其实还要有一个方法，来分配当前已经失效的callpoint，和没有分配callPoint的对象; 应该在checkpoint等前，可以后面再改;
        for (CallPoint callPoint : allCallPoint()) {
            TimeCostPrint timeCostPrint = new TimeCostPrint(logger, 60, "checkpointLogicThread: " + callPoint.toString());
            checkpointLogicThread(callPoint);
            timeCostPrint.print();
        }
    }

    /**
     * 这里默认所有的数据库类都能遍历到，因为在遍历这个之前需要，将所有没有归属某一个callPoint的数据库类修正掉;
     */
    public void checkpointLogicThread(CallPoint callPoint) {
        checkpointLogicThreadInternal(callPoint);
    }

    private void checkpointLogicThreadInternal(CallPoint callPoint) {
        logger.info("checkpoint table Start table {} ", getName());

        List<TRecord<K, V>> addTRecordCache = new ArrayList<>();
        List<TRecord<K, V>> modifyTRecordCache = new ArrayList<>();
        List<TRecord<K, V>> removeTRecordCache = new ArrayList<>();

        Map<K, Long> key2MemVersionMap = new HashMap<>();


        for (TRecord<K, V> kvtRecord : getCacheList()) {
            if (!kvtRecord.checkCallPoint(callPoint)) {
                continue;
            }
            K key = kvtRecord.getKey();

            if (!kvtRecord.isModified()) {
                continue;
            }

            int state = kvtRecord.getState();
            switch (state) {
                case TRecord.ADD:
                    addTRecordCache.add(kvtRecord);
                    break;
                case TRecord.GET:
                    modifyTRecordCache.add(kvtRecord);
                    break;
                case TRecord.REMOVE:
                    removeTRecordCache.add(kvtRecord);
                    break;
            }

            key2MemVersionMap.put(key, kvtRecord.getValue().getMemVersion());
        }

        if (addTRecordCache.isEmpty() &&
                modifyTRecordCache.isEmpty() &&
                removeTRecordCache.isEmpty()) {
            return;
        }

        logger.info("checkpoint checkpointMdbSaveDB table Start table {} addSize {} modifySize {} removeSize {}  ",
                getName(), addTRecordCache.size(), modifyTRecordCache.size(), removeTRecordCache.size());
        boolean addSuccess = true;
        boolean modifySuccess = true;
        boolean removeSuccess = true;

        if (!addTRecordCache.isEmpty()) {
            try {
                Map<K, V> tempMap = createValueMap(addTRecordCache);
                addSuccess = dbExec(createBatchSaveDBReq(tempMap), callPoint);
            } catch (Exception e) {
                logger.error("checkpoint addCache fail table {} ", this.getName(), e);
                addSuccess = false;
            }
        }

        if (!modifyTRecordCache.isEmpty()) {
            try {
                Map<K, V> tempMap = createValueMap(modifyTRecordCache);
                modifySuccess = dbExec(createBatchSaveDBReq(tempMap), callPoint);
            } catch (Exception e) {
                logger.error("checkpoint modifyCache fail table {} ", this.getName(), e);
                modifySuccess = false;
            }
        }

        if (!removeTRecordCache.isEmpty()) {
            try {
                Map<K, V> tempMap = removeTRecordCache.stream().collect(Collectors.toMap(TRecord::getKey, TRecord::getValue));
                removeSuccess = dbExec(createBatchRemoveDBReq(tempMap), callPoint);
            } catch (Exception e) {
                logger.error("checkpoint removeCache fail table {} ", this.getName(), e);
                removeSuccess = false;
            }
        }

        if (removeSuccess) {
            for (TRecord<K, V> tRecord : removeTRecordCache) {
                long memVersion = key2MemVersionMap.get(tRecord.getKey());
                tRecord.checkpointRefreshState(memVersion);
                if (tRecord.isRemoveState() && !tRecord.isModified()
                        && cache.remove(tRecord.getKey(), tRecord)) {
                    findFailCache.put(tRecord.getKey(), defaultValue);
                }
            }
        }

        if (addSuccess) {
            for (TRecord<K, V> tRecord : addTRecordCache) {
                long memVersion = key2MemVersionMap.get(tRecord.getKey());
                tRecord.checkpointRefreshState(memVersion);
            }
        }

        if (modifySuccess) {
            for (TRecord<K, V> tRecord : modifyTRecordCache) {
                long memVersion = key2MemVersionMap.get(tRecord.getKey());
                tRecord.checkpointRefreshState(memVersion);
            }
        }

        // 保存失败时 dbVersion 不推进，记录会保持待保存状态。

    }




    public void tick(long currTime) {
        long count = countGetMiss.getAndSet(0);
        if (count > 0) {
            allCountGetMiss += count;
            //Set<Object> failKeySet = findFailCache.asMap().keySet();
            logger.info(" tableName {}  allCountGetMiss {} countGetMiss {} TICK_INTERVAL {}",
                    getName(), allCountGetMiss, count, Mdb.TICK_INTERVAL);
        }

        for (TRecord<K, V> kvtRecord : getCacheList()) {
            kvtRecord.checkWillCallPoint(currTime);
        }
    }

    private Map<K, V> createValueMap(Collection<TRecord<K, V>> records) {
        Map<K, V> result = new LinkedHashMap<>(records.size());
        for (TRecord<K, V> record : records) {
            result.put(record.getKey(), record.getValue());
        }
        return result;
    }

    /**
     * 定时把30分钟还没有访问的数据清理
     */
    public void tickClearCache() {
        if (getMdb().isClosing()) {
            logger.info("tickClearCache ignore because mdb is closing table {}", getName());
            return;
        }
        //todo 这里遍历当前所有的的callpoint; 然后其实还要有一个方法，来分配当前已经失效的callpoint，和没有分配callPoint的对象; 应该在checkpoint等前，可以后面再改;
        for (CallPoint callPoint : allCallPoint()) {
            TimeCostPrint timeCostPrint = new TimeCostPrint(logger, 30, "tickClearCacheLogicThread: " + callPoint.toString());
            tickClearCacheLogicThread(callPoint);
            timeCostPrint.print();
        }
    }

    /** 清理过期记录时通过单调递增的 memVersion 判断保存期间是否又有新修改。 */
    public void tickClearCacheLogicThread(CallPoint callPoint) {
        long curNano = System.nanoTime();
        long expairNano = TimeCostPrint.millsNano * TimeCostPrint.minuteMills * 30;


        logger.info("tickClearCache tickClearCacheLogicThread start tableName {}  ", this.getName());

        List<TRecord<K, V>> expireList = new ArrayList<>();
        for (TRecord<K, V> kvtRecord : cache.values()) {
            // 30分钟没访问删除
            if (curNano - kvtRecord.getLastAccessTime() < expairNano) {
                continue;
            }

            if (!kvtRecord.checkCallPoint(callPoint)) {
                continue;
            }

            expireList.add(kvtRecord);
        }

        if (expireList.isEmpty()) {
            return;
        }

        logger.info("tickClearCache tickClearCacheMdbSaveDB start tableName {} expireListSize {} ", this.getName(), expireList.size());

        int successRecordSize = 0;
        for (TRecord<K, V> one : expireList) {
            TRecord<K, V> currentRecord = cache.get(one.getKey());
            if (currentRecord != one
                    || curNano - one.getLastAccessTime() < expairNano) {
                continue;
            }

            if (!one.isModified()) {
                cache.remove(one.getKey(), one);
                continue;
            }

            long snapshotVersion = one.getValue().getMemVersion();

            if (one.flush("tickClearCache")) {
                one.checkpointRefreshState(snapshotVersion);
                TRecord<K, V> currentRecord2 = cache.get(one.getKey());
                if (currentRecord2 == one && !one.isModified()) {
                    cache.remove(one.getKey(), one);
                    successRecordSize++;
                }
            }
        }

        logger.info("tickClearCache tickClearCacheMdbSaveDB summary tableName {}   expireListSize {}  successRecordSize {}",
                this.getName(), expireList.size(), successRecordSize);
    }



    boolean flushPlayer(long playerId) {
        return flush(playerKey(playerId));
    }

    DBReq createSyncRequest(TRecord<K, V> record) {
        Map<K, V> singleRecord = Map.of(record.getKey(), record.getValue());
        DBReq request = switch (record.getState()) {
            case TRecord.ADD, TRecord.GET -> createBatchSaveDBReq(singleRecord);
            case TRecord.REMOVE -> createBatchRemoveDBReq(singleRecord);
            default -> throw new IllegalStateException("未知记录状态: " + record.getState());
        };
        return request;
    }

    /**
     * flush 是按玩家同步多张表的。
     * 如果每张表成功后立刻删自己的 cache，但后面的表失败了，就会出现：
     * 部分表 cache 已删
     * 部分表 cache 还在
     * 但玩家状态仍然是 LOAD_FINISH
     * 玩家重新登录时看到 LOAD_FINISH 会直接复用 cache，不会重新完整加载，导致 cache 不完整。
     * 所以现在先保留所有 cache，等玩家所有表都 flush 成功后，再统一清理。
     */
    private boolean flush(K key) {
        if (getMdb().isClosing()) {
            logger.error("flush mdb is closing table {} key {}", getName(), key);
            return false;
        }

        findFailCache.invalidate(key);
        TRecord<K, V> record = cache.get(key);
        if (record == null) {
            return true;
        }

        if (!record.isModified()) {
            return true;
        }
        long snapshotVersion = record.getValue().getMemVersion();

        if (record.flush("flush")) {
            // 这里不管中间flush变化了;会在上层检测
            /**
             * 这里没用，比如A表flush 但是数据还没清理，B表又flush，这个时候有一个协程拿着get出来的数据进行更新，那我不是这个数据还是修改了，
             * 但是我在clearPlayerCache  这边检测的话，就可以避免这个情况 可以在调用clearPlayerCache 前先检测下 这样也不会影响failLoad
             */
            record.checkpointRefreshState(snapshotVersion);
            return true;
        }

        return false;
    }

    public void close() {
        close(true);
    }

    void close(boolean runCheckpoint) {
        long currTime = System.currentTimeMillis();
        logger.info("{}  table stop begin {}  ", getName(), mdb.service.getId());
        if (runCheckpoint) {
            checkpoint(true);
        }
        if (runCheckpoint) {
            int dirtyRecordCount = 0;
            for (TRecord<K, V> record : cache.values()) {
                if (record.isModified()) {
                    dirtyRecordCount++;
                }
            }
            if (dirtyRecordCount > 0) {
                logger.error("MDB停服checkpoint未完成，仍有脏数据将被清理: service={} table={} dirtyRecordCount={}",
                        mdb.service.getId(), getName(), dirtyRecordCount);
            }
        }
        cache.clear();
        long endTime = System.currentTimeMillis();
        logger.info("{}  mdb stop end {}  costMill {}", getName(), mdb.service.getId(), endTime - currTime);
    }


    public abstract String getName();

    public boolean isSupportFlush() {
        return getMdb().isSupportFlush();
    }


    Mdb getMdb() {
        return mdb;
    }

    public void setMdb(Mdb mdb) {
        this.mdb = mdb;
    }

    /** 为离线快照恢复提供 DBReq 序列化器，恢复时不需要启动或构造 Mdb。 */
    public final void initializeForRecovery(ProtostuffSerializer serializer) {
        if (mdb != null) {
            throw new IllegalStateException("已绑定 Mdb 的 TTable 不能初始化为离线恢复表: " + getName());
        }
        recoverySerializer = Objects.requireNonNull(serializer, "serializer");
    }


    Collection<TRecord<K, V>> getCacheList() {
        return cache.values();
    }

    /** 按 DB Bean 主键读取当前缓存记录，删除状态下允许用原 Bean 定位 tombstone。 */
    @SuppressWarnings("unchecked")
    TRecord<K, V> getCache(DirtyObject object) {
        V value = (V) object;
        K key = Objects.requireNonNull(getPrimaryKey(value), "DB 对象主键不能为空");
        TRecord<K, V> record = cache.get(key);
        if (record == null || (!record.isRemoveState() && record.getValue() != value)) {
            throw new DBException("立即同步对象不属于当前 MDB 缓存: table=" + getName()
                    + ", key=" + key);
        }
        return record;
    }

    void deleteCache(K key) {
        findFailCache.invalidate(key);
        cache.remove(key);
    }

    void deletePlayerCache(long playerId) {
        K key = playerKey(playerId);
        findFailCache.invalidate(key);
        cache.remove(key);
    }

    boolean hasModifiedPlayerCache(long playerId) {
        K key = playerKey(playerId);
        TRecord<K, V> record = cache.get(key);
        return record != null && record.isModified();
    }

    private void checkAccess(K key, boolean forLoad) {
        getMdb().checkPlayerAccess(key, forLoad);
    }

    @SuppressWarnings("unchecked")
    private K playerKey(long playerId) {
        return (K) Long.valueOf(playerId);
    }

    public V getExec(DBReq dbReq, CallPoint callPoint, boolean sync) {
        if (callPoint == null) {
            logger.error("callPoint == null  type {} DBReq {}", dbReq.getDbOpType(), dbReq);
        }
        DBRsp dbRsp = getMdb().executeDbReq(callPoint, dbReq, sync);
        //todo 这里进行报错之类的
        return parseGetDBRsp(dbRsp);
    }

    public boolean dbExec(DBReq dbReq, CallPoint callPoint) {
        if (callPoint == null) {
            logger.error("callPoint == null  type {} DBReq {}", dbReq.getDbOpType(), dbReq);
        }
        DBRsp dbRsp = getMdb().executeDbReq(callPoint, dbReq, false);
        //todo 这里进行报错之类的
        return dbRsp != null && dbRsp.isSuccess();
    }

    public CallPoint findDBServiceCallPoint(K key) {
        return mdb.findDBServiceCallPoint(key);
    }


    /**
     * 获取所有callPoint
     */
    public List<CallPoint> allCallPoint() {
        return mdb.allCallPoint();
    }


    protected abstract V newValue();

    protected abstract Schema<V> snapshotSchema();

    public final V newSnapshotValue() {
        return newValue();
    }

    public final Schema<V> getSnapshotSchema() {
        return snapshotSchema();
    }

    public final K getSnapshotPrimaryKey(V value) {
        return getPrimaryKey(value);
    }

    protected final byte[] serializeWithProtostuff(V value, Schema<V> schema) {
        Mdb currentMdb = mdb;
        ProtostuffSerializer serializer = currentMdb == null
                ? recoverySerializer : currentMdb.getProtostuffSerializer();
        if (serializer == null) {
            throw new DBException("TTable 未绑定 Mdb 或恢复序列化器: table=" + getName());
        }
        return serializer.serialize(value, schema);
    }

    V newRemoveValue(K key) {
        V value = newValue();
        setPrimaryKey(value, key);
        return value;
    }

    protected void validateKeyValue(K key, V value) {
    }

    public abstract DBReq createCreateTableDBReq();

    public abstract DBReq createGetDBReq(K key);

    public final DBReq createSaveDBReq(K key, V value) {
        validateKeyValue(key, value);
        return serializeSaveDBReq(value);
    }

    protected abstract DBReq serializeSaveDBReq(V value);

    public abstract DBReq createBatchGetDBReq(Map<K, V> map);

    public final DBReq createBatchSaveDBReq(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("batch map 不能为空");
        }
        for (Map.Entry<K, V> entry : map.entrySet()) {
            validateKeyValue(entry.getKey(), entry.getValue());
        }
        return serializeBatchSaveDBReq(map);
    }

    protected abstract DBReq serializeBatchSaveDBReq(Map<K, V> map);

    public abstract DBReq createRemoveDBReq(K key);

    public abstract DBReq createBatchRemoveDBReq(Map<K, V> map);

    public abstract V parseGetDBRsp(DBRsp rsp);

    public abstract Map<K, V> parseBatchGetDBRsp(DBRsp rsp);

    protected abstract K getPrimaryKey(V value);

    protected abstract void setPrimaryKey(V value, K key);


}
