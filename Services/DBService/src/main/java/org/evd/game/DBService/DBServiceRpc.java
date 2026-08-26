package org.evd.game.DBService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Service;

/** DBService 数据库执行 RPC 入口。 */
@Actor
@RpcHandler
public final class DBServiceRpc {
    @Rpc
    public DBRsp dbExec(DBReq dbReq) {
        return Service.getCurrent(DBService.class).dbExec(dbReq);
    }
}
