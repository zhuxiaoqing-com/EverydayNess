package org.evd.game.ConnService;

import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.function.*;
        import org.evd.game.runtime.ClientSessionRef;
        import org.evd.game.runtime.Chunk;
        import org.evd.game.common.serializeBean.ConnInfo;

/**
* 根据ConnServiceService生成的rpc分发类
*/
public class ConnServiceImpl extends RPCImplBase {
    public final static class EnumCall{
        public final static int ENUM_CONNSERVICE_STRING_CON = 0;
        public final static int ENUM_CONNSERVICE_VOID_CON1 = 1;
        public final static int ENUM_CONNSERVICE_VOID_CON2 = 2;
        public final static int ENUM_CONNSERVICE_VOID_FORWARDCLIENTCMD_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK = 3;
        public final static int ENUM_CONNSERVICE_VOID_PUSHTOCLIENT_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK = 4;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST1 = 5;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_COMMON_SERIALIZEBEAN_CONNINFO = 6;
    }

    @Override
    public Object getMethodFunction(Service serv, int methodKey) {
        ConnService service = (ConnService) serv;
        switch (methodKey){
            case EnumCall.ENUM_CONNSERVICE_STRING_CON:
                return (ReturnFunction0<String>)service::con;
            case EnumCall.ENUM_CONNSERVICE_VOID_CON1:
                return (Function0)service::con1;
            case EnumCall.ENUM_CONNSERVICE_VOID_CON2:
                return (Function0)service::con2;
            case EnumCall.ENUM_CONNSERVICE_VOID_FORWARDCLIENTCMD_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK:
                return (Function3<org.evd.game.runtime.ClientSessionRef, Integer, org.evd.game.runtime.Chunk>)service::forwardClientCmd;
            case EnumCall.ENUM_CONNSERVICE_VOID_PUSHTOCLIENT_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK:
                return (Function3<org.evd.game.runtime.ClientSessionRef, Integer, org.evd.game.runtime.Chunk>)service::pushToClient;
            case EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST1:
                return (Function0)service.requireCurrentMailbox(ConnTestProxy.class)::connTest1;
            case EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_COMMON_SERIALIZEBEAN_CONNINFO:
                return (Function3<Integer, Object, org.evd.game.common.serializeBean.ConnInfo>)service.requireCurrentMailbox(ConnTestProxy.class)::connTest2;
            default:
                return null;
        }
    }
}
