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
        public final static int ENUM_DBSERVICE_ORG_EVD_GAME_RUNTIME_DB_SERIALIZE_DBRSP_DBEXEC_ORG_EVD_GAME_RUNTIME_DB_SERIALIZE_DBREQ = 0;
    }

    /**
    * @see org.evd.game.DBService.DBService#dbExec()
    */
    public static org.evd.game.runtime.Db.serialize.DBRsp dbExec(CallPoint remote, org.evd.game.runtime.Db.serialize.DBReq dbReq){
        Service service = Service.getCurrent();
        return (org.evd.game.runtime.Db.serialize.DBRsp)service.callWait(remote, EnumCall.ENUM_DBSERVICE_ORG_EVD_GAME_RUNTIME_DB_SERIALIZE_DBRSP_DBEXEC_ORG_EVD_GAME_RUNTIME_DB_SERIALIZE_DBREQ, new Object[]{dbReq});
    }
    public static org.evd.game.runtime.Db.serialize.DBRsp dbExec(CallPoint remote, org.evd.game.runtime.Db.serialize.DBReq dbReq, long timeoutMillis){
        Service service = Service.getCurrent();
        return (org.evd.game.runtime.Db.serialize.DBRsp)service.callWait(remote, EnumCall.ENUM_DBSERVICE_ORG_EVD_GAME_RUNTIME_DB_SERIALIZE_DBRSP_DBEXEC_ORG_EVD_GAME_RUNTIME_DB_SERIALIZE_DBREQ, new Object[]{dbReq}, timeoutMillis);
    }
}
