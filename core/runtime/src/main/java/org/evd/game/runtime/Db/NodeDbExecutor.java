package org.evd.game.runtime.Db;

import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;

/**
 * Node 本地数据库入口。它不是 Service，不参与 Service 注册和 RPC 路由。
 */
public interface NodeDbExecutor extends DBExecInterface, AutoCloseable {
    @Override
    void close();
}
