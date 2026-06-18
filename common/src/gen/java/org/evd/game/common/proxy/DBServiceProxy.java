package org.evd.game.common.proxy;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DBReq;

/**
* 根据DBServiceService生成的代理类
*/
public final class DBServiceProxy {

    private DBServiceProxy() {
    }


    public final static class EnumCall{
        public final static int ENUM_DBSERVICE_DBEXEC_0 = 0;
    }

    /**
    * @see org.evd.game.DBService.DBService#dbExec()
    */
    public static DBRsp dbExec(CallPoint remote, DBReq dbReq){
        Service service = Service.getCurrent();
        return (DBRsp)service.callWait(remote, EnumCall.ENUM_DBSERVICE_DBEXEC_0, new Object[]{dbReq});
    }

    public static DBRsp dbExec(CallPoint remote, DBReq dbReq, long timeoutMillis){
        Service service = Service.getCurrent();
        return (DBRsp)service.callWait(remote, EnumCall.ENUM_DBSERVICE_DBEXEC_0, new Object[]{dbReq}, timeoutMillis);
    }

}
