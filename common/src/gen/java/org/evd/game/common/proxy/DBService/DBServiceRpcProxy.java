package org.evd.game.common.proxy.DBService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DBReq;

/**
* 根据DBServiceRpcService生成的代理类
*/
public final class DBServiceRpcProxy implements DBExecInterface {

    private static final DBServiceRpcProxy INSTANCE = new DBServiceRpcProxy();

    private DBServiceRpcProxy() {
    }

    public static DBServiceRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_DBSERVICERPC_DBEXEC_0 = 0;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<DBRsp> callDbExec(CallPoint remote, DBReq dbReq){
        return RpcResult.call(() -> inst().dbExec(remote, dbReq));
    }



    /**
    * 对应源方法: org.evd.game.DBService.DBServiceRpc#dbExec()
    */
    public DBRsp dbExec(CallPoint remote, DBReq dbReq){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.DB);
        }
        return (DBRsp)service.callWait(remote, EnumCall.ENUM_DBSERVICERPC_DBEXEC_0, new Object[]{dbReq});
    }


}
