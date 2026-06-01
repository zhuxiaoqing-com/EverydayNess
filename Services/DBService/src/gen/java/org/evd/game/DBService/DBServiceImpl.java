package org.evd.game.DBService;

import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.function.*;
        import org.evd.game.runtime.Db.serialize.DBRsp;
        import org.evd.game.runtime.Db.serialize.DBReq;

/**
* 根据DBServiceService生成的rpc分发类
*/
public class DBServiceImpl extends RPCImplBase {
    public final static class EnumCall{
        public final static int ENUM_DBSERVICE_ORG_EVD_GAME_DB_SERIALIZE_DBRSP_DBEXEC_ORG_EVD_GAME_DB_SERIALIZE_DBREQ = 0;
    }

    @Override
    public Object getMethodFunction(Service serv, int methodKey) {
        DBService service = (DBService) serv;
        switch (methodKey){
            case EnumCall.ENUM_DBSERVICE_ORG_EVD_GAME_DB_SERIALIZE_DBRSP_DBEXEC_ORG_EVD_GAME_DB_SERIALIZE_DBREQ:
                return (ReturnFunction1<DBRsp, DBReq>)service::dbExec;
            default:
                return null;
        }
    }
}
