package org.evd.game.runtime.Db;

import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;

/**
 * Node 本地数据库入口。它不是 Service，不参与 Service 注册和 RPC 路由。
 */
public interface NodeDbExecutor extends DBExecInterface, AutoCloseable {
    /** 初始化远程数据库入口，使用固定连接池配置。 */
    void init();

    /** 初始化 NODE_LOCAL 数据库入口，按实际 MDB Service 数量计算连接池。 */
    void init(int mdbServiceCount);

    DBRsp doExecSync(DBReq dbReq);

    @Override
    void close();
}
