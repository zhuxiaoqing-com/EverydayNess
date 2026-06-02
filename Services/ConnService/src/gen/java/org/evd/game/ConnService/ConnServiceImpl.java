package org.evd.game.ConnService;

import org.evd.game.common.serializeBean.ConnInfo;
import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.function.*;
        import org.evd.game.runtime.client.ClientSessionRef;

/**
* 根据ConnServiceService生成的rpc分发类
*/
public class ConnServiceImpl extends RPCImplBase {
    public final static class EnumCall{
        public final static int ENUM_CONNSERVICE_STRING_CON = 0;
        public final static int ENUM_CONNSERVICE_VOID_CON1 = 1;
        public final static int ENUM_CONNSERVICE_VOID_CON4 = 2;
        public final static int ENUM_CONNSERVICE_VOID_FORWARDCLIENTCMD_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK = 3;
        public final static int ENUM_CONNSERVICE_VOID_PUSHTOCLIENT_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK = 4;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_SERIALIZEBEAN_CONNINFO = 5;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST3 = 6;
    }

    @Override
    public Object getMethodFunction(Service serv, int methodKey) {
        ConnService service = (ConnService) serv;
        switch (methodKey){
            case EnumCall.ENUM_CONNSERVICE_STRING_CON:
                return (ReturnFunction0<String>)service::con;
            case EnumCall.ENUM_CONNSERVICE_VOID_CON1:
                return (Function0)service::con1;
            case EnumCall.ENUM_CONNSERVICE_VOID_CON4:
                return (Function0)service::con4;
            case EnumCall.ENUM_CONNSERVICE_VOID_FORWARDCLIENTCMD_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK:
                return (Function3<ClientSessionRef, Integer, org.evd.game.runtime.Chunk>)service::forwardClientCmd;
            case EnumCall.ENUM_CONNSERVICE_VOID_PUSHTOCLIENT_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK:
                return (Function3<ClientSessionRef, Integer, org.evd.game.runtime.Chunk>)service::pushToClient;
            case EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_SERIALIZEBEAN_CONNINFO:
                return (Function3<Integer, Object, ConnInfo>)service.requireCurrentActor(ConnTestProxy.class)::connTest2;
            case EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST3:
                return (Function0)service.requireCurrentActor(ConnTestProxy.class)::connTest3;
            default:
                return null;
        }
    }
}
