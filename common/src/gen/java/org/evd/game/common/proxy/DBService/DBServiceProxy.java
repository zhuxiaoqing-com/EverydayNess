package org.evd.game.common.proxy.DBService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DBReq;

/**
* 根据DBServiceService生成的代理类
*/
public final class DBServiceProxy implements DBExecInterface {

    private static final DBServiceProxy INSTANCE = new DBServiceProxy();

    private DBServiceProxy() {
    }

    public static DBServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_DBSERVICE_DBEXEC_0 = 0;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<DBRsp> callDbExec(CallPoint remote, DBReq dbReq){
        return RpcResult.call(() -> inst().dbExec(remote, dbReq));
    }



    /**
    * 对应源方法: org.evd.game.DBService.DBService#dbExec()
    */
    public DBRsp dbExec(CallPoint remote, DBReq dbReq){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.DB);
        }
        return (DBRsp)service.callWait(remote, EnumCall.ENUM_DBSERVICE_DBEXEC_0, new Object[]{dbReq});
    }


}
