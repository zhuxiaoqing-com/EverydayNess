package org.evd.game.common.proxy.ConnService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.annotation.ActorType;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.Chunk;

/**
* 根据ConnServiceService生成的代理类
*/
public final class ConnServiceProxy {

    private static final ConnServiceProxy INSTANCE = new ConnServiceProxy();

    private ConnServiceProxy() {
    }

    public static ConnServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNSERVICE_CON_0 = 0;
        public final static int ENUM_CONNSERVICE_CON1_1 = 1;
        public final static int ENUM_CONNSERVICE_CON4_2 = 2;
        public final static int ENUM_CONNSERVICE_PUSHTOCLIENT_3 = 3;
    }

    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#con()
    */
    public String con(CallPoint remote){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_CON_0, new Object[]{});
    }

    public String con(CallPoint remote, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_CON_0, new Object[]{}, timeoutMillis);
    }

    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#con1()
    */
    public void con1(CallPoint remote){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_CON1_1, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#con4()
    */
    public void con4(CallPoint remote){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_CON4_2, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#pushToClient()
    */
    public void pushToClient(long actorUniqueId, ClientSessionRef session, int msgId, Chunk body){
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_CONNSERVICE_PUSHTOCLIENT_3, new Object[]{session, msgId, body});
    }


}
