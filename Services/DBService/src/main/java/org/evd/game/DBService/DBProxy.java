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
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.Db.NodeDbExecutor;
import org.evd.game.runtime.support.exception.ServiceStoppingException;
import reactor.core.publisher.Mono;


/**
 * 数据库实现入口。
 *
 * 独立 DBService 与 NODE_LOCAL 都复用这一实现；差别只在请求从 RPC 到达，
 * 还是由本 Node 的 Service 直接调用。
 */
public final class DBProxy implements NodeDbExecutor {
    private final StorageEngine storageEngine;
    private final DBCache dbCache;
    private volatile boolean closed;

    public DBProxy() {
        DbYml dbConfig = GlobalYml.requireDbConfig();
        if (!"mysql".equalsIgnoreCase(dbConfig.getDb().getEngine())) {
            throw new IllegalArgumentException("unsupported db engine: " + dbConfig.getDb().getEngine());
        }
        DbMysqlYml mysqlConfig = dbConfig.getDb().getMysql();
        storageEngine = new StorageMysql(this, new LoggerMysql(mysqlConfig), dbConfig.getDb().getStorage());
        if (dbConfig.getDb().getStorage().isEnableMemoryCache()) {
            dbCache = new DBCache(storageEngine);
        } else {
            dbCache = null;
        }
    }

    @Override
    public DBRsp dbExec(CallPoint ignored, DBReq dbReq) {
        if (closed) {
            throw new ServiceStoppingException("database is stopping");
        }

        DBRsp dbRsp = new DBRsp();
        dbRsp.setSuccess(true);
        try {
            DBRsp cache = storageEngine.cache(dbReq, dbCache);
            if (cache != null) {
                return cache;
            }
            switch (dbReq.getDbOpType()) {
                case DbOpType.CREATE_TABLE -> storageEngine.initTable(dbReq);
                case DbOpType.GET -> dbRsp = storageEngine.find(dbReq);
                case DbOpType.BATCH_GET -> dbRsp = storageEngine.findBatch(dbReq);
                case DbOpType.SAVE -> storageEngine.replace(dbReq);
                case DbOpType.BATCH_SAVE -> storageEngine.replaceBatch(dbReq);
                case DbOpType.REMOVE -> storageEngine.remove(dbReq);
                case DbOpType.BATCH_REMOVE -> storageEngine.removeBatch(dbReq);
                default -> throw new IllegalArgumentException("unsupported db operation: " + dbReq.getDbOpType());
            }
        } catch (Exception e) {
            return new DBRsp(e.getMessage());
        }
        return dbRsp;
    }

    public <T> T awaitDb(Mono<T> mono, long timeoutMillis) {
        Service service = Service.getCurrent();
        if (service == null) {
            throw new IllegalStateException("DBProxy must be called from a Service coroutine");
        }
        return service.awaitCompletionStage(mono.toFuture(), timeoutMillis);
    }

    public void stop(boolean force) {
        boolean closeStorage = force;
        try {
            if (dbCache != null) {
                dbCache.stop(force);
            }
            closeStorage = true;
        } finally {
            if (closeStorage && !closed) {
                closed = true;
                storageEngine.close();
            }
        }
    }

    @Override
    public void close() {
        stop(true);
    }
}
