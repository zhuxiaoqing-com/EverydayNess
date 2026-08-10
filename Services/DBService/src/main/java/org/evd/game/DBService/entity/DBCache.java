package org.evd.game.DBService.entity;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.DBService.DBService;
import org.evd.game.runtime.config.GlobalConfig;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.config.DbConfig;

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
    private final DBService dbService;
    private final long flushIntervalMs;
    private final Map<String, TableCache> cache = new HashMap<>();

    private boolean stopping;
    private boolean flushing;
    private long nextFlushTime;

    public DBCache(DBService dbService) {
        DbConfig dbConfig = GlobalConfig.requireDbConfig();
        this.flushIntervalMs = dbConfig.getDb().getStorage().getCacheFlushMs();
        if (flushIntervalMs <= 0) {
            throw new IllegalArgumentException("cacheFlushMs must be greater than 0: " + flushIntervalMs);
        }
        this.dbService = dbService;
        this.nextFlushTime = dbService.getTimeCurrent() + flushIntervalMs;
    }

    public void tick() {
        if (stopping || flushing || dbService.getTimeCurrent() < nextFlushTime) {
            return;
        }

        flushing = true;
        try {
            dbService.launchCoroutine(this::flushWithLock);
        } catch (RuntimeException | Error e) {
            flushing = false;
            throw e;
        }
    }

    public void stop(boolean force) {
        stopping = true;
        try (ContinuationLockScope ignored = dbService.awaitCoroutineLockScope(LockType.DB_CACHE, this, 0)) {
            flushOnce();
            if (hasPendingRecords()) {
                throw new IllegalStateException("DB cache stop flush failed, pendingRecordCount="
                        + pendingRecordCount());
            }
        } catch (RuntimeException | Error e) {
            if (!force) {
                stopping = false;
                nextFlushTime = dbService.getTimeCurrent() + flushIntervalMs;
            }
            throw e;
        }
    }

    private void flushWithLock() {
        try (ContinuationLockScope ignored = dbService.awaitCoroutineLockScope(LockType.DB_CACHE, this)) {
            if (!stopping) {
                flushOnce();
            }
        } finally {
            flushing = false;
            if (!stopping) {
                nextFlushTime = dbService.getTimeCurrent() + flushIntervalMs;
            }
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
                List<DBReq> dbReqs = dbService.storageEngine.buildSaveDBReq(
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
            case DbOpType.SAVE:
                dbService.storageEngine.replace(dbReq);
                break;
            case DbOpType.BATCH_SAVE:
                dbService.storageEngine.replaceBatch(dbReq);
                break;
            case DbOpType.REMOVE:
                dbService.storageEngine.remove(dbReq);
                break;
            case DbOpType.BATCH_REMOVE:
                dbService.storageEngine.removeBatch(dbReq);
                break;
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
