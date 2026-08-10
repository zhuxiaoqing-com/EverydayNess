package org.evd.game.runtime.Db.table;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.table.util.TimeCostPrint;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.support.TwoTuple;
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


    public V get(K key) {
        TRecord<K, V> tRecord = cache.get(key);
        if (tRecord != null) {
            if (tRecord.isRemoveState()) {
                return null;
            }
            return tRecord.get();
        }


        // 不存在在数据库找个新的 然后返回
        if (findFailCache.getIfPresent(key) != null) {
            return null;
        }
        CallPoint callPoint = findDBServiceCallPoint(key);
        if (callPoint == null) {
            throw new DBException("DBService未连接: table=" + getName() + ", key=" + key);
        }
        // 加协程锁
        try (ContinuationLockScope ignored = mdb.service.awaitCoroutineLockScope(LockType.TABLE_RECORD, new TwoTuple<>(getName(), key))) {
            tRecord = cache.get(key);
            if (tRecord != null) {
                if (tRecord.isRemoveState()) {
                    return null;
                }
                return tRecord.get();
            }

            if (findFailCache.getIfPresent(key) != null) {
                return null;
            }

            V v = getExec(createGetDBReq(key), callPoint);

            if (v != null) {
                tRecord = new TRecord<>(this, key, v, TRecord.GET, callPoint);
                cache.put(key, tRecord);
                return tRecord.getValue();
            }

            findFailCache.put(key, defaultValue);
            // getMiss
            countGetMiss.incrementAndGet();
            return null;
        }

    }

    public boolean add(K key, V value) {
        return add(key, value, false);
    }

    public boolean add(K key, V value, boolean immediately) {
        if (value == null) {
            throw new NullPointerException("value is null");
        }

        TRecord<K, V> tRecord = cache.get(key);
        if (tRecord != null) {
            tRecord.add(value);
            return true;
        }

        try (ContinuationLockScope ignored = mdb.service.awaitCoroutineLockScope(
                LockType.TABLE_RECORD, new TwoTuple<>(getName(), key))) {
            tRecord = cache.get(key);
            if (tRecord != null) {
                tRecord.add(value);
                return true;
            }

            tRecord = new TRecord<>(this, key, value, TRecord.ADD, findDBServiceCallPoint(key));
            cache.put(key, tRecord);
            findFailCache.invalidate(key);
            return true;
        }
    }

    public boolean remove(K key) {
        TRecord<K, V> tRecord = cache.get(key);
        if (tRecord != null) {
            tRecord.remove();
            return true;
        }

        try (ContinuationLockScope ignored = mdb.service.awaitCoroutineLockScope(
                LockType.TABLE_RECORD, new TwoTuple<>(getName(), key))) {
            tRecord = cache.get(key);
            if (tRecord != null) {
                tRecord.remove();
                return true;
            }

            // 没有找到就直接新建一个删除标记。
            tRecord = new TRecord<K, V>(this, key, newValue(), TRecord.REMOVE, findDBServiceCallPoint(key));
            cache.put(key, tRecord);
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
        logger.info("checkpoint table Start table {} ", getName());

        List<TRecord<K, V>> addTRecordCache = new ArrayList<>();
        List<TRecord<K, V>> modifyTRecordCache = new ArrayList<>();
        List<TRecord<K, V>> removeTRecordCache = new ArrayList<>();


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

            // 将add状态刷新成get状态
            kvtRecord.checkpointRefreshState(state);
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
                if (tRecord.isRemoveState() && cache.remove(tRecord.getKey(), tRecord)) {
                    findFailCache.put(tRecord.getKey(), defaultValue);
                }
            }
        }

        // 全部成功了
        if (addSuccess && modifySuccess && removeSuccess) {
            return;
        }

        // 这里就算logic线程的cache里已经没有数据了也没事,反正只是改了这个类,其他都没改
        if (!addSuccess) {
            for (TRecord<K, V> tRecord : addTRecordCache) {
                tRecord.checkPointFail();
            }
        }

        if (!modifySuccess) {
            for (TRecord<K, V> tRecord : modifyTRecordCache) {
                tRecord.checkPointFail();
            }
        }

        if (!removeSuccess) {
            for (TRecord<K, V> tRecord : removeTRecordCache) {
                tRecord.checkPointFail();
            }
        }

    }


    public static final int TICK_INTERVAL = 1000 * 60;
    public long nextTickTime;

    public void tick(long currTime) {
        if (nextTickTime <= 0) {
            nextTickTime = currTime + TICK_INTERVAL;
            return;
        }
        if (currTime < nextTickTime) {
            return;
        }
        nextTickTime = currTime + TICK_INTERVAL;
        long count = countGetMiss.getAndSet(0);
        if (count > 0) {
            allCountGetMiss += count;
            //Set<Object> failKeySet = findFailCache.asMap().keySet();
            logger.info(" tableName {}  allCountGetMiss {} countGetMiss {} TICK_INTERVAL {}",
                    getName(), allCountGetMiss, count, TICK_INTERVAL);
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

    public void tickClearCacheLogicThread(CallPoint callPoint) {
        long curNano = System.nanoTime();
        long expairNano = TimeCostPrint.millsNano * TimeCostPrint.minuteMills * 30;


        logger.info("tickClearCache tickClearCacheLogicThread start tableName {}  ", this.getName());

        List<TRecord<K, V>> expireList = new ArrayList<>();
        List<TRecord<K, V>> deleteList = new ArrayList<>();
        for (TRecord<K, V> kvtRecord : cache.values()) {
            // 30分钟没访问删除
            if (curNano - kvtRecord.getLastAccessTime() < expairNano) {
                continue;
            }

            if (!kvtRecord.checkCallPoint(callPoint)) {
                continue;
            }

            if (!kvtRecord.isModified()) {
                // 遍历结束后统一删除，避免修改正在遍历的HashMap。
                deleteList.add(kvtRecord);
                continue;
            }
            // 这里先将其设置为没修改过，然后等存好数据库数据以后,再返回，如果还是没修改过，那就进行删除cache，如果修改过了，就不动;
            int flushState = kvtRecord.getState();
            kvtRecord.checkpointRefreshState(flushState);
            expireList.add(kvtRecord);
        }

        for (TRecord<K, V> record : deleteList) {
            cache.remove(record.getKey(), record);
        }

        if (expireList.isEmpty()) {
            return;
        }

        logger.info("tickClearCache tickClearCacheMdbSaveDB start tableName {} expireListSize {} ", this.getName(), expireList.size());

        List<TRecord<K, V>> successRecord = new ArrayList<>();
        for (TRecord<K, V> one : expireList) {
            boolean flush = one.flush("tickClearCache");
            if (flush) {
                successRecord.add(one);
            } else {
                one.checkPointFail();
            }
        }

        logger.info("tickClearCache tickClearCacheMdbSaveDB summary tableName {}   expireListSize {}  successRecordSize {}",
                this.getName(), expireList.size(), successRecord.size());

        if (successRecord.isEmpty()) {
            return;
        }

        // 这里先将其设置为没修改过，然后等存好数据库数据以后,再返回，如果还是没修改过，那就进行删除cache，如果修改过了，就不动;
        for (TRecord<K, V> savedRecord : successRecord) {
            TRecord<K, V> currRecord = cache.get(savedRecord.getKey());
            if (currRecord != savedRecord) {
                //logger.error("tickClearCacheMdbSaveDB currRecord!= savedRecord 请查找问题！！！ table {} currRecord {} savedRecord {} ", getName(), currRecord, savedRecord);
                continue;
            }

            // 修改过
            if (savedRecord.isModified()) {
                continue;
            }
            // 删除数据
            cache.remove(savedRecord.getKey());
        }
    }


    public void close() {
        close(true);
    }

    void close(boolean runCheckpoint) {
        long currTime = System.currentTimeMillis();
        logger.info("{}  table stop begin {}  ", getName(), mdb.service.getId());
        if (runCheckpoint) {
            checkpoint(true);
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


    public Collection<TRecord<K, V>> getCacheList() {
        return cache.values();
    }

    public void deleteCache(K key) {
        findFailCache.invalidate(key);
        cache.remove(key);
    }

    public V getExec(DBReq dbReq, CallPoint callPoint) {
        if (callPoint == null) {
            logger.error("callPoint == null  type {} DBReq {}", dbReq.getDbOpType(), dbReq);
        }
        DBRsp dbRsp = getMdb().getDbExecInterface().dbExec(callPoint, dbReq);
        //todo 这里进行报错之类的
        return parseGetDBRsp(dbRsp);
    }

    public boolean dbExec(DBReq dbReq, CallPoint callPoint) {
        if (callPoint == null) {
            logger.error("callPoint == null  type {} DBReq {}", dbReq.getDbOpType(), dbReq);
        }
        DBRsp dbRsp = getMdb().getDbExecInterface().dbExec(callPoint, dbReq);
        //todo 这里进行报错之类的
        return dbRsp.isSuccess();
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


    /**
     * 仅限于内部使用
     */
    public TRecord<K, V> getTRecordByCache(K key) {
        return cache.get(key);
    }

    protected abstract V newValue();

    protected void validateKeyValue(K key, V value) {
    }

    public abstract DBReq createCreateTableDBReq();

    public abstract DBReq createGetDBReq(K key);

    public final DBReq createSaveDBReq(K key, V value) {
        validateKeyValue(key, value);
        return serializeSaveDBReq(value);
    }

    protected abstract DBReq serializeSaveDBReq(V value);

    public abstract DBReq createRemoveDBReq(K key);

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

    public abstract DBReq createBatchRemoveDBReq(Map<K, V> map);

    public abstract V parseGetDBRsp(DBRsp rsp);

    public abstract Map<K, V> parseBatchGetDBRsp(DBRsp rsp);


}
