package org.evd.game.DBService.entity;

import org.evd.game.DBService.storage.mysql.StorageEngine;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.Db.serialize.DbTableField;
import org.evd.game.runtime.Db.serialize.MysqlReq;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.ymlconfig.DbYml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author zhuxiaoqing
 * @Description: DBCache
 * @Date 2026/7/1 16:13
 **/
public class DBCache {
    private static final Logger log = LoggerFactory.getLogger(DBCache.class);

    private final Service cacheOwner;
    private final StorageEngine storageEngine;
    private final long tickTimerId;
    private final Map<String, TableCache> cache = new HashMap<>();

    private boolean stopping;
    private boolean flushing;

    public DBCache(StorageEngine storageEngine) {
        DbYml dbConfig = GlobalYml.requireDbConfig();
        long flushIntervalMs = dbConfig.getDb().getStorage().getCacheFlushMs();
        if (flushIntervalMs <= 0) {
            throw new IllegalArgumentException("cacheFlushMs must be greater than 0: " + flushIntervalMs);
        }
        this.cacheOwner = Service.getCurrent();
        this.storageEngine = storageEngine;
        this.tickTimerId = cacheOwner.timerScheduler().scheduleRepeated(
                cacheOwner.getTimeCurrent(), flushIntervalMs, false, this::tick);
    }

    public void tick() {
        if (stopping || flushing) {
            return;
        }

        flushing = true;
        try {
            cacheOwner.launchCoroutine(this::flushWithLock);
        } catch (RuntimeException | Error e) {
            flushing = false;
            throw e;
        }
    }

    public void stop(boolean force) {
        stopping = true;
        try (ContinuationLockScope ignored = cacheOwner.awaitCoroutineLockScope(LockType.DB_CACHE, this, 0)) {
            flushOnce();
            if (hasPendingRecords()) {
                throw new IllegalStateException("DB cache stop flush failed, pendingRecordCount="
                        + pendingRecordCount());
            }
        } catch (RuntimeException | Error e) {
            if (!force) {
                stopping = false;
            }
            throw e;
        }
        cacheOwner.timerScheduler().cancel(tickTimerId);
    }

    private void flushWithLock() {
        try (ContinuationLockScope ignored = cacheOwner.awaitCoroutineLockScope(LockType.DB_CACHE, this)) {
            if (!stopping) {
                flushOnce();
            }
        } finally {
            flushing = false;
        }
    }

    /** 先缓存事务中的变更，再执行 MySQL 事务；成功后移除已持久化的缓存项。 */
    public <T> T executeTransaction(List<DBReq> requests, Supplier<T> operation) {
        if (stopping) {
            throw new IllegalStateException("DB cache is stopping");
        }

        Map<CacheKey, TRecord> transactionRecords = new HashMap<>();
        for (DBReq request : requests) {
            DBRsp cacheResponse = storageEngine.cache(request, this);
            if (cacheResponse == null || !cacheResponse.isSuccess()) {
                String message = cacheResponse == null
                        ? "transaction cache update returned no response"
                        : cacheResponse.getExceptionMessage();
                throw new IllegalStateException(message);
            }

            MysqlReq mysqlReq = request.getMysqlReq();
            TableCache tableCache = getTableCache(mysqlReq.getTableName());
            for (DbTableField field : mysqlReq.getTablFieldList()) {
                Object key = field.getTableKey();
                transactionRecords.put(
                        new CacheKey(tableCache, key), tableCache.getCache().get(key));
            }
        }

        T result = operation.get();
        try (ContinuationLockScope ignored = cacheOwner.awaitCoroutineLockScope(LockType.DB_CACHE, this)) {
            for (Map.Entry<CacheKey, TRecord> entry : transactionRecords.entrySet()) {
                CacheKey key = entry.getKey();
                if (key.tableCache().getCache().get(key.key()) == entry.getValue()) {
                    key.tableCache().getCache().remove(key.key());
                }
            }
        }
        return result;
    }

    private void flushOnce() {
        List<TableCache> tableCaches = new ArrayList<>(cache.values());
        for (TableCache tableCache : tableCaches) {
            HashMap<Object, TRecord> snapshot = new HashMap<>(tableCache.getCache());
            if (snapshot.isEmpty()) {
                continue;
            }

            try {
                List<DBReq> dbReqs = storageEngine.buildSaveDBReq(
                        tableCache.getTableName(), snapshot.values());
                for (DBReq dbReq : dbReqs) {
                    save(dbReq);
                }

                for (Map.Entry<Object, TRecord> entry : snapshot.entrySet()) {
                    tableCache.getCache().remove(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                log.error("DB cache flush failed, tableName={}", tableCache.getTableName(), e);
            }
        }
    }


    public void save(DBReq dbReq) {
        switch (dbReq.getDbOpType()) {
            case DbOpType.SAVE -> storageEngine.replace(dbReq);
            case DbOpType.BATCH_SAVE -> storageEngine.replaceBatch(dbReq);
            case DbOpType.REMOVE -> storageEngine.remove(dbReq);
            case DbOpType.BATCH_REMOVE -> storageEngine.removeBatch(dbReq);
            default -> throw new IllegalArgumentException("unsupported cache flush operation: " + dbReq.getDbOpType());
        }
    }


    public TableCache getTableCache(String tableName) {
        return cache.computeIfAbsent(tableName, a -> new TableCache(tableName));
    }

    private boolean hasPendingRecords() {
        for (TableCache tableCache : cache.values()) {
            if (!tableCache.getCache().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int pendingRecordCount() {
        int count = 0;
        for (TableCache tableCache : cache.values()) {
            count += tableCache.getCache().size();
        }
        return count;
    }

    private record CacheKey(TableCache tableCache, Object key) {
    }

}
