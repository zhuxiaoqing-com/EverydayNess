package org.evd.game.DBService.entity;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.DBService.storage.mysql.StorageEngine;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.ymlconfig.DbYml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhuxiaoqing
 * @Description: DBCache
 * @Date 2026/7/1 16:13
 **/
@Slf4j
public class DBCache {
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
        if (cacheOwner == null) {
            throw new IllegalStateException("DBCache must be created from a Service");
        }
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

}
