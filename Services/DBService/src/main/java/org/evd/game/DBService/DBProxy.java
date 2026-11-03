package org.evd.game.DBService;

import org.evd.game.DBService.entity.DBCache;
import org.evd.game.DBService.storage.mysql.LoggerMysql;
import org.evd.game.DBService.storage.mysql.StorageEngine;
import org.evd.game.DBService.storage.mysql.StorageMysql;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.ymlconfig.DbYml;
import org.evd.game.runtime.ymlconfig.DbMysqlYml;
import org.evd.game.runtime.ymlconfig.DbMysqlRuntimeYml;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.Db.NodeDbExecutor;
import org.evd.game.runtime.support.exception.ServiceStoppingException;
import org.evd.game.runtime.continuation.Task;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 数据库实现入口。
 *
 * 独立 DBService 与 NODE_LOCAL 都复用这一实现；差别只在请求从 RPC 到达，
 * 还是由本 Node 的 Service 直接调用。
 */
public final class DBProxy implements NodeDbExecutor {
    private static final int NODE_LOCAL_MIN_POOL_INITIAL_SIZE = 4;

    private StorageEngine storageEngine;
    private DBCache dbCache;
    private volatile boolean closed;
    /** 当前线程正在执行同步 DB 请求时为 true，awaitDb 据此选择阻塞等待。 */
    private final ThreadLocal<Boolean> syncExecution = new ThreadLocal<>();

    public DBProxy() {
    }

    @Override
    public synchronized void init() {
        init(PoolSize.remote(), true);
    }

    /** NODE_LOCAL 使用者在启动阶段已经统计完成，连接池只按这个数量计算一次。 */
    @Override
    public synchronized void init(int mdbServiceCount) {
        init(PoolSize.nodeLocal(mdbServiceCount), true);
    }

    /** 单独恢复进程不启用依赖 Service 定时器的 DB 内存缓存。 */
    public synchronized void initForRecovery() {
        init(PoolSize.remote(), false);
    }

    private void init(PoolSize poolSize, boolean enableMemoryCache) {
        if (closed) {
            throw new IllegalStateException("DBProxy is already closed");
        }
        if (storageEngine != null) {
            throw new IllegalStateException("DBProxy is already initialized");
        }
        DbYml dbConfig = GlobalYml.requireDbConfig();
        if (!"mysql".equalsIgnoreCase(dbConfig.getDb().getEngine())) {
            throw new IllegalArgumentException("unsupported db engine: " + dbConfig.getDb().getEngine());
        }
        DbMysqlYml mysqlConfig = dbConfig.getDb().getMysql();
        storageEngine = new StorageMysql(this,
                new LoggerMysql(mysqlConfig, poolSize.initialSize(), poolSize.maxSize()),
                dbConfig.getDb().getStorage());
        if (enableMemoryCache && dbConfig.getDb().getStorage().isEnableMemoryCache()) {
            dbCache = new DBCache(storageEngine);
        } else {
            dbCache = null;
        }
    }

    @Override
    public DBRsp dbExec(CallPoint remote, DBReq dbReq) {
        if (closed) {
            throw new ServiceStoppingException("database is stopping");
        }
        StorageEngine currentStorageEngine = storageEngine;
        if (currentStorageEngine == null) {
            throw new IllegalStateException("DBProxy is not initialized");
        }

        if (dbReq.getDbOpType() == DbOpType.TRANSACTION) {
            return executeTransaction(currentStorageEngine, dbReq);
        }
        return executeRequest(currentStorageEngine, dbReq);
    }

    /** 在独立恢复进程中同步执行一条数据库请求。 */
    public DBRsp dbExecRecovery(DBReq dbReq) {
        Boolean previous = syncExecution.get();
        try {
            syncExecution.set(Boolean.TRUE);
            return dbExec(null, dbReq);
        } finally {
            restoreSyncExecution(previous);
        }
    }

    /** 使用生成的 CREATE_TABLE 请求携带表元数据，不在恢复时执行 DDL。 */
    public void registerRecoveryTable(DBReq createTableRequest) {
        if (storageEngine == null) {
            throw new IllegalStateException("DBProxy is not initialized");
        }
        if (!(storageEngine instanceof StorageMysql storageMysql)) {
            throw new IllegalStateException("快照恢复只支持 MySQL: engine="
                    + storageEngine.getClass().getName());
        }
        if (createTableRequest == null || createTableRequest.getMysqlReq() == null) {
            throw new IllegalArgumentException("恢复表元数据请求不完整");
        }
        var mysqlReq = createTableRequest.getMysqlReq();
        storageMysql.registerRecoveryTable(mysqlReq.getTableName(), mysqlReq.getTableMeta());
    }

    private DBRsp executeTransaction(StorageEngine currentStorageEngine, DBReq dbReq) {
        try {
            if (dbReq.getTransactionRequests() == null || dbReq.getTransactionRequests().isEmpty()) {
                throw new IllegalArgumentException("transaction requests 不能为空");
            }
            Runnable execute = () -> currentStorageEngine.executeTransaction(dbReq.getTransactionRequests());
            if (dbCache == null) {
                execute.run();
            } else {
                dbCache.executeTransaction(dbReq.getTransactionRequests(), () -> {
                    execute.run();
                    return null;
                });
            }
            DBRsp response = new DBRsp();
            response.setSuccess(true);
            return response;
        } catch (Exception e) {
            return new DBRsp(e.getMessage());
        }
    }

    private DBRsp executeRequest(StorageEngine currentStorageEngine, DBReq dbReq) {
        try {
            DBRsp cache = currentStorageEngine.cache(dbReq, dbCache);
            if (cache != null) {
                return cache;
            }
        } catch (Exception e) {
            return new DBRsp(e.getMessage());
        }
        DBRsp dbRsp = new DBRsp();
        dbRsp.setSuccess(true);
        try {
            switch (dbReq.getDbOpType()) {
                case DbOpType.CREATE_TABLE -> currentStorageEngine.initTable(dbReq);
                case DbOpType.GET -> dbRsp = currentStorageEngine.find(dbReq);
                case DbOpType.BATCH_GET -> dbRsp = currentStorageEngine.findBatch(dbReq);
                case DbOpType.SAVE -> currentStorageEngine.replace(dbReq);
                case DbOpType.BATCH_SAVE -> currentStorageEngine.replaceBatch(dbReq);
                case DbOpType.REMOVE -> currentStorageEngine.remove(dbReq);
                case DbOpType.BATCH_REMOVE -> currentStorageEngine.removeBatch(dbReq);
                default -> throw new IllegalArgumentException("unsupported db operation: " + dbReq.getDbOpType());
            }
        } catch (Exception e) {
            return new DBRsp(e.getMessage());
        }
        return dbRsp;
    }

    @Override
    public DBRsp doExecSync(DBReq dbReq) {
        Boolean previous = syncExecution.get();
        Service service = Service.peekCurrent();
        Task.ContinuationWrapper continuation = null;
        if (service != null && service.continuationRuntime().isContinuation()) {
            continuation = service.requireRunningContinuation();
            // 同步 DB 请求必须留在当前协程栈；任何协程等待都会在真正挂起前失败。
            continuation.enterNonYieldingScope();
        }

        try {
            syncExecution.set(Boolean.TRUE);
            return dbExec(null, dbReq);
        } finally {
            restoreSyncExecution(previous);
            if (continuation != null) {
                continuation.exitNonYieldingScope();
            }
        }
    }

    public <T> T awaitDb(Mono<T> mono, long timeoutMillis) {
        // 等待层多等 5 秒，给底层 operation.timeout() 的取消、连接清理和结果回传留出时间。正常打不了协程超时的;
        long awaitTimeoutMillis = timeoutMillis + 5000L;
        if (Boolean.TRUE.equals(syncExecution.get())) {
            return mono.block(Duration.ofMillis(awaitTimeoutMillis));
        }
        Service service = Service.getCurrent();
        return service.awaitCompletionStage(mono.toFuture(), awaitTimeoutMillis);
    }

    private void restoreSyncExecution(Boolean previous) {
        if (previous == null) {
            syncExecution.remove();
        } else {
            syncExecution.set(previous);
        }
    }

    public synchronized void stop(boolean force) {
        StorageEngine currentStorageEngine = storageEngine;
        if (currentStorageEngine == null) {
            closed = true;
            return;
        }
        boolean closeStorage = force;
        try {
            if (dbCache != null) {
                dbCache.stop(force);
            }
            closeStorage = true;
        } finally {
            if (closeStorage && !closed) {
                closed = true;
                currentStorageEngine.close();
            }
        }
    }

    @Override
    public void close() {
        stop(true);
    }

    private record PoolSize(int initialSize, int maxSize) {
        private static PoolSize remote() {
            DbMysqlRuntimeYml runtimeConfig = GlobalYml.requireDbConfig().getDb().getMysql().getRuntime();
            return new PoolSize(runtimeConfig.getRemotePoolInitialSize(), runtimeConfig.getRemotePoolMaxSize());
        }

        private static PoolSize nodeLocal(int mdbServiceCount) {
            if (mdbServiceCount <= 0) {
                throw new IllegalArgumentException("mdbServiceCount must be positive");
            }
            DbMysqlRuntimeYml runtimeConfig = GlobalYml.requireDbConfig().getDb().getMysql().getRuntime();
            long initialSize = (long) mdbServiceCount * runtimeConfig.getLocalInitialSizePerService();
            long maxSize = (long) mdbServiceCount * runtimeConfig.getLocalMaxSizePerService();
            int resolvedMaxSize = Math.toIntExact(Math.min(maxSize, runtimeConfig.getLocalMaxPoolSize()));
            // n 大于 30 时 2n 会超过池上限，必须保证 initialSize 不大于 maxSize。
            int resolvedInitialSize = Math.toIntExact(Math.clamp(initialSize, NODE_LOCAL_MIN_POOL_INITIAL_SIZE, resolvedMaxSize));
            return new PoolSize(resolvedInitialSize, resolvedMaxSize);
        }
    }
}
