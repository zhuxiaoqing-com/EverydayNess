package org.evd.game.ConnService;

import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.function.*;
        import org.evd.game.common.serializeBean.ConnInfo;

/**
* 根据ConnServiceService生成的rpc分发类
*/
public class ConnServiceImpl extends RPCImplBase {
    public final static class EnumCall{
        public final static int ENUM_CONNSERVICE_STRING_CON = 0;
        public final static int ENUM_CONNSERVICE_VOID_CON1 = 1;
        public final static int ENUM_CONNSERVICE_VOID_CON2 = 2;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST1 = 3;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_RUNTIME_SERIALIZEBEAN_CONNINFO = 4;
    }
    private ConnTestProxy connTestProxy;

    private ConnTestProxy connTestProxy() {
        if (connTestProxy == null) {
            connTestProxy = new ConnTestProxy();
        }
        return connTestProxy;
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
            case EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST1:
                return (Function0)connTestProxy()::connTest1;
            case EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_RUNTIME_SERIALIZEBEAN_CONNINFO:
                return (Function3<Integer, Object, ConnInfo>)connTestProxy()::connTest2;
            default:
                return null;
        }
    }
}
