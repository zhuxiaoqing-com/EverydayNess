package org.evd.game.common.proxy;

import org.evd.game.runtime.Service;
import org.evd.game.common.location.MessageLocationSender;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorType;
import org.evd.game.common.serializeBean.ConnInfo;

/**
* 根据ConnTestProxyService生成的代理类
*/
public final class ConnTestProxyProxy {

    private static final ConnTestProxyProxy INSTANCE = new ConnTestProxyProxy();

    private ConnTestProxyProxy() {
    }

    public static ConnTestProxyProxy inst() {
        return INSTANCE;
    }

    private static MessageLocationSender createMessageLocationSender() {
        return new MessageLocationSender(ConnTestProxyProxy::queryActorAddress);
    }

    private static org.evd.game.runtime.actor.ActorAddress queryActorAddress(ActorId actorId) {
        return LocationServiceProxy.inst().get(locationServiceRemote(), actorId);
    }

    private static org.evd.game.runtime.call.CallPoint locationServiceRemote() {
        org.evd.game.runtime.call.CallPoint remote =
                org.evd.game.runtime.config.DistributeConfig.getNodeByServiceClass(
                        "org.evd.game.LocationService.LocationService",
                        0L);
        if (remote == null) {
            throw new IllegalStateException(
                    "找不到 LocationService 服务路由: org.evd.game.LocationService.LocationService");
        }
        return remote;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNTESTPROXY_CONNTEST2_4 = 4;
        public final static int ENUM_CONNTESTPROXY_CONNTEST3_5 = 5;
    }

    /**
    * @see org.evd.game.ConnService.ConnTestProxy#connTest2()
    */
    public void connTest2(long actorUniqueId, int a, Object b, ConnInfo connInfo){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        createMessageLocationSender().send(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST2_4, new Object[]{a, b, connInfo});
    }


    /**
    * @see org.evd.game.ConnService.ConnTestProxy#connTest3()
    */
    public void connTest3(long actorUniqueId){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        createMessageLocationSender().send(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST3_5, new Object[]{});
    }


}
