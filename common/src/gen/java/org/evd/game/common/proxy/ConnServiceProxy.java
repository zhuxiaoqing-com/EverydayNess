package org.evd.game.common.proxy;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
        import org.evd.game.runtime.client.ClientSessionRef;
        import org.evd.game.runtime.Chunk;

/**
* 根据ConnServiceService生成的代理类
*/
public final class ConnServiceProxy {

    private ConnServiceProxy() {
    }


    public final static class EnumCall{
        public final static int ENUM_CONNSERVICE_STRING_CON = 0;
        public final static int ENUM_CONNSERVICE_VOID_CON1 = 1;
        public final static int ENUM_CONNSERVICE_VOID_CON4 = 2;
        public final static int ENUM_CONNSERVICE_VOID_PUSHTOCLIENT_ORG_EVD_GAME_RUNTIME_CLIENT_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK = 3;
    }

    /**
    * @see org.evd.game.ConnService.ConnService#con()
    */
    public static String con(CallPoint remote){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_STRING_CON, new Object[]{});
    }
    public static String con(CallPoint remote, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_STRING_CON, new Object[]{}, timeoutMillis);
    }
    /**
    * @see org.evd.game.ConnService.ConnService#con1()
    */
    public static void con1(CallPoint remote){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_VOID_CON1, new Object[]{});
    }
    /**
    * @see org.evd.game.ConnService.ConnService#con4()
    */
    public static void con4(CallPoint remote){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_VOID_CON4, new Object[]{});
    }
    /**
    * @see org.evd.game.ConnService.ConnService#pushToClient()
    */
    public static void pushToClient(CallPoint remote, org.evd.game.runtime.client.ClientSessionRef session, int msgId, org.evd.game.runtime.Chunk body){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_VOID_PUSHTOCLIENT_ORG_EVD_GAME_RUNTIME_CLIENT_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK, new Object[]{session, msgId, body});
    }
}
