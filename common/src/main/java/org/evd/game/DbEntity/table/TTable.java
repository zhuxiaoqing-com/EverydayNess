package org.evd.game.DbEntity.table;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.evd.game.base.DirtyObject;
import org.evd.game.DbEntity.serialize.DBRsp;
import org.evd.game.common.proxy.DBServiceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public abstract class TTable<K, V extends DirtyObject> {
    public static final Object defaultValue = new Object();
    private static final Logger logger = LoggerFactory.getLogger(TTable.class);

    //private TTableCache<K, V> cache;
    private final Map<K, TRecord<K, V>> cache = new HashMap<>(500);
    Cache<Object, Object> findFailCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(30,TimeUnit.MINUTES)
            .executor(Runnable::run)
            .build();

    private final AtomicLong countGetMiss = new AtomicLong();
    private long allCountGetMiss = 0;

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
        V v = _find(key);
        if (v != null) {
            tRecord = new TRecord<>(this, key, null, TRecord.GET);
            cache.put(key, tRecord);
        } else {
            findFailCache.put(key, defaultValue);
            // getMiss
            countGetMiss.incrementAndGet();
        }

        return tRecord == null ? null : tRecord.getValue();

    }

    public boolean add(K key, V value) {
      return  add(key, value, false);
    }

    public boolean add(K key, V value, boolean immediately) {
        if (value == null) {
            throw new NullPointerException("value is null");
        }

        TRecord<K, V> tRecord = cache.get(key);
        if (tRecord != null) {
            tRecord.add(value);
        } else {
            tRecord = new TRecord<>(this, key, value, TRecord.ADD);
            cache.put(key, tRecord);
            findFailCache.invalidate(key);
        }
        return true;
    }

    public boolean remove(K key) {
        TRecord<K, V> tRecord = cache.get(key);
        if (tRecord != null) {
            tRecord.remove();
            return true;
        }
        // 没有找到就直接新建一个
        tRecord = new TRecord<K, V>(this, key, newValue(), TRecord.REMOVE);
        cache.put(key, tRecord);
        /**
         * 未加载 remove 现在能正常留下删除标记了，但这里没有清理 findFailCache。
         * 如果这个 key 之前查库未命中过，之后又经历 remove -> add -> tickClearCache 写回 DB，旧的未命中缓存可能在 cache 被清掉后继续让 get 返回 null。
         * 这个窗口比较绕，但清掉 findFailCache 成本很低。
         */
        findFailCache.invalidate(key);

        return true;
    }


    /**
     * 查找数据库 等地方
     */
    private V _find(K key) {
        // todo 到DBService查询
        /*DBServiceProxy.dbExec(null, )
        marshal()*/
        return null;
        //return tables.getStorage().find(getName(), marshalKey(key));
    }


    /**
     * 将单个key保存到数据库  玩家离线时调用;
     * 应该还有个判断 该表是否属于玩家 属于玩家才在玩家离线时调用;
     */
    /*public void flush(K key) {
        TRecord<K, V> kvtRecord = _flush(key);
        if (kvtRecord != null) {
            kvtRecord.flush();
        }
    }*/

/*

    public void checkpoint(boolean sync) {
        logger.info("checkpoint table Start table {} ", getName());
        MdbInterface mdbInterface = getMdb().getMdbInterface();
        if (!sync && getMdb().isClosing()) {
            logger.info("checkpoint ignore because mdb is closing table {}", getName());
            return;
        }
        if(sync) {
            checkpointLogicThread(sync);
        } else {
            mdbInterface.allExecute(() -> checkpointLogicThread(sync));
        }
    }

    public void checkpointLogicThread(boolean sync){
        MdbInterface mdbInterface = getMdb().getMdbInterface();
        // 线程不符
        if (!sync) {
            if (!mdbInterface.checkThreadName(getSerializeLogicThreadName())) {
                return;
            }
        }
        logger.info("checkpoint checkpointLogicThread table Start table {} ", getName());

        //检测那些key可以在该线程
        HashMap<String, String> addCache = new HashMap<>();
        HashMap<String, String> modifyCache = new HashMap<>();
        HashMap<String, String> removeCache = new HashMap<>();

        List<TRecord> addTRecordCache = new ArrayList<>();
        List<TRecord> modifyTRecordCache = new ArrayList<>();
        List<TRecord> removeTRecordCache = new ArrayList<>();

        for (TRecord<K,V> kvtRecord : getCacheList()) {
            K key = kvtRecord.getKey();
            if (!sync) {
                if (!mdbInterface.checkKeyBelongCurrThread(key)) {
                    continue;
                }
            }

            if (!kvtRecord.isModified(getMdb(), false)) {
                continue;
            }
            String valueJson = kvtRecord.marshalValue();
            int state = kvtRecord.getState();
            switch (state) {
                case TRecord.ADD:
                    addCache.put(kvtRecord.marshalKey(), valueJson);
                    addTRecordCache.add(kvtRecord);
                    break;
                case TRecord.GET:
                    modifyCache.put(kvtRecord.marshalKey(), valueJson);
                    modifyTRecordCache.add(kvtRecord);
                    break;
                case TRecord.REMOVE:
                    removeCache.put(kvtRecord.marshalKey(), "");
                    removeTRecordCache.add(kvtRecord);
                    break;
            }

            // 将add状态刷新成get状态
            kvtRecord.checkpointRefreshState(state);
        }

        if (addCache.isEmpty() &&
                modifyCache.isEmpty() &&
                removeCache.isEmpty()) {
            return;
        }

        if(sync) {
            checkpointMdbSaveDB(addCache,
                    modifyCache,
                    removeCache,
                    addTRecordCache,
                    modifyTRecordCache,
                    removeTRecordCache,sync);
        } else {
            // 序列化好了 抛回去
            if (!getMdb().putDBExecutor(a -> checkpointMdbSaveDB(addCache,
                    modifyCache,
                    removeCache,
                    addTRecordCache,
                    modifyTRecordCache,
                    removeTRecordCache, sync))) {
                restoreCheckpointFail(addTRecordCache, modifyTRecordCache, removeTRecordCache);
            }
        }
    }

    public void checkpointMdbSaveDB(HashMap<String, String> addCache,
                                      HashMap<String, String> modifyCache,
                                      HashMap<String, String> removeCache,
                                      List<TRecord> addTRecordCache,
                                      List<TRecord> modifyTRecordCache,
                                      List<TRecord> removeTRecordCache,
                                    boolean sync
    ){
        logger.info("checkpoint checkpointMdbSaveDB table Start table {} addSize {} modifySize {} removeSize {}  ",
                getName(), addCache.size(), modifyCache.size(), removeCache.size());
        boolean addSuccess = true;
        boolean modifySuccess = true;
        boolean removeSuccess = true;

        try {
            getTables().getStorage().replaceBatch(getName(), addCache);
        } catch (Exception e) {
            logger.error("checkpoint addCache fail table {} ", this.getName(), e);
            addSuccess = false;
        }

        try {
            getTables().getStorage().replaceBatch(getName(), modifyCache);
        } catch (Exception e) {
            logger.error("checkpoint modifyCache fail table {} ", this.getName(), e);
            modifySuccess = false;
        }

        try {
            getTables().getStorage().removeBatch(getName(), removeCache.keySet());
        } catch (Exception e) {
            logger.error("checkpoint removeCache fail table {} ", this.getName(), e);
            removeSuccess = false;
        }

        // 全部成功了
        if (addSuccess && modifySuccess && removeSuccess) {
            return;
        }



        TRecord any = Stream.of(addTRecordCache, modifyTRecordCache, removeTRecordCache)
                .flatMap(List::stream)
                .findAny().orElse(null);

        // 抛会逻辑线程恢复数据
        boolean finalAddSuccess = addSuccess;
        boolean finalmodifySuccess = modifySuccess;
        boolean finalremoveSuccess = removeSuccess;
        Runnable runnable = () -> restoreCheckpointFail(
                finalAddSuccess ? Collections.emptyList() : addTRecordCache,
                finalmodifySuccess ? Collections.emptyList() : modifyTRecordCache,
                finalremoveSuccess ? Collections.emptyList() : removeTRecordCache);

        if (sync) {
            logger.error("数据库数据同步失败！！！！具体日志 table {} ", this.getName());
            runnable.run();
        } else {
            getMdb().getMdbInterface().execute(getSerializeLogicThreadName(), any.getKey(), runnable);
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
    }

    *//**
     * 定时把30分钟还没有访问的数据清理
     *//*
    public void tickClearCache() {
        if (getMdb().isClosing()) {
            logger.info("tickClearCache ignore because mdb is closing table {}", getName());
            return;
        }
        getMdb().getMdbInterface().allExecute(()->tickClearCacheLogicThread());
    }

    public void tickClearCacheLogicThread() {
        long curNano = System.nanoTime();
        long expairNano = TimeCostPrint.millsNano * TimeCostPrint.minuteMills * 30;

        MdbInterface mdbInterface = getMdb().getMdbInterface();
        // 线程不符
        if (!mdbInterface.checkThreadName(getSerializeLogicThreadName())) {
            return;
        }

        logger.info("tickClearCache tickClearCacheLogicThread start tableName {}  ", this.getName());

        List<FourTuple<TRecord<K, V>, Integer, String, String>> expireList = new ArrayList<>();
        for (TRecord<K, V> kvtRecord : cache.values()) {
            // 30分钟没访问删除
            if (curNano - kvtRecord.getLastAccessTime() < expairNano) {
                continue;
            }
            if (!mdbInterface.checkKeyBelongCurrThread(kvtRecord.getKey())) {
                continue;
            }
            if (!kvtRecord.isModified(getMdb(), true)) {
                // 没有变化过 直接删除;
                cache.remove(kvtRecord.getKey());
                continue;
            }
            String valueJson = kvtRecord.marshalValue();
            // 这里先将其设置为没修改过，然后等存好数据库数据以后,再返回，如果还是没修改过，那就进行删除cache，如果修改过了，就不动;
            int flushState = kvtRecord.getState();
            kvtRecord.checkpointRefreshState(flushState);
            expireList.add(new FourTuple<>(kvtRecord, flushState, kvtRecord.marshalKey(), valueJson));
        }

        if(expireList.isEmpty()) {
            return;
        }

        // 抛到DB线程进行存储
        if (!getMdb().putDBExecutor(a -> tickClearCacheMdbSaveDB(expireList))) {
            restoreTickClearCacheFail(expireList);
        }
    }




    public void tickClearCacheMdbSaveDB(List<FourTuple<TRecord<K, V>, Integer, String, String>> expireList){
        logger.info("tickClearCache tickClearCacheMdbSaveDB start tableName {} expireListSize {} ", this.getName(), expireList.size());

        List<TRecord<K, V>> successRecord = new ArrayList<>();
        for (FourTuple<TRecord<K, V>, Integer, String, String> tuple : expireList) {
            TRecord<K, V> one = tuple.getOne();
            boolean flush = one.flush("tickClearCache", tuple.getTwo(), tuple.getThree(), tuple.getFour());
            if(flush){
                successRecord.add(one);
            }
        }

        logger.info("tickClearCache tickClearCacheMdbSaveDB summary tableName {}   expireListSize {}  successRecordSize {}",
                this.getName(), expireList.size(), successRecord.size());

        if(successRecord.isEmpty()) {
            return;
        }

        // 抛会玩家线程进行处理
        TRecord<K, V> any = expireList.get(0).getOne();

        // 抛会逻辑线程删除cache数据
        getMdb().getMdbInterface().execute(getSerializeLogicThreadName(), any.getKey(), ()->{
            // 这里先将其设置为没修改过，然后等存好数据库数据以后,再返回，如果还是没修改过，那就进行删除cache，如果修改过了，就不动;
            for (TRecord<K, V> savedRecord : successRecord) {
                TRecord<K, V> currRecord = cache.get(savedRecord.getKey());
                if(currRecord != savedRecord) {
                    //logger.error("tickClearCacheMdbSaveDB currRecord!= savedRecord 请查找问题！！！ table {} currRecord {} savedRecord {} ", getName(), currRecord, savedRecord);
                    continue;
                }

                // 修改过
                if(savedRecord.isModified(getMdb(), false)){
                    continue;
                }
                // 删除数据
                cache.remove(savedRecord.getKey());
            }
        });
    }

    private void restoreCheckpointFail(List<TRecord> addTRecordCache,
                                       List<TRecord> modifyTRecordCache,
                                       List<TRecord> removeTRecordCache) {
        // 这里就算logic线程的cache里已经没有数据了也没事,反正只是改了这个类,其他都没改
        for (TRecord tRecord : addTRecordCache) {
            tRecord.checkPointFail();
        }
        for (TRecord tRecord : modifyTRecordCache) {
            tRecord.checkPointFail();
        }
        for (TRecord tRecord : removeTRecordCache) {
            tRecord.checkPointFail();
        }
    }

    private void restoreTickClearCacheFail(List<FourTuple<TRecord<K, V>, Integer, String, String>> expireList) {
        for (FourTuple<TRecord<K, V>, Integer, String, String> tuple : expireList) {
            tuple.getOne().checkPointFail();
        }
    }


    public Tables getTables() {
        return tables;
    }

    public void close() {

    }


    public abstract String getName();

    public boolean isSupportFlush() {
        return meta != null && meta.isSupportFlush();
    }

    void setMeta(Table meta) {
        this.meta = meta;
    }

    Table getMeta() {
        return meta;
    }

    public String getSerializeLogicThreadName() {
        if(meta == null) {
            return "";
        }
        return meta.getSerializeLogicThreadName();
    }

    Mdb getMdb() {
        return tables.getMdb();
    }

    public void setTables(Tables tables) {
        this.tables = tables;
    }*/

    public Collection<TRecord<K, V>> getCacheList() {
        return cache.values();
    }

    public void deleteCache(K key) {
        findFailCache.invalidate(key);
        cache.remove(key);
    }


    /**
     * 仅限于内部使用
     */
    public TRecord<K, V> getTRecordByCache(K key) {
        return cache.get(key);
    }

    protected abstract V newValue();

    protected abstract String marshal(DBRsp dbRsp);

    protected abstract V deepCopy(V value);

    protected abstract K unmarshalKey(String os) throws Exception;

    protected abstract V unmarshalValue(String os) throws Exception;

}
