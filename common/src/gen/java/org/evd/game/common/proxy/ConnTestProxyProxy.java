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

    private ConnTestProxyProxy() {
    }

    private static MessageLocationSender createMessageLocationSender() {
        return new MessageLocationSender(ConnTestProxyProxy::queryActorAddress);
    }

    private static org.evd.game.runtime.actor.ActorAddress queryActorAddress(ActorId actorId) {
        return LocationServiceProxy.get(locationServiceRemote(), actorId);
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
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_COMMON_SERIALIZEBEAN_CONNINFO = 0;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST3 = 0;
    }

    /**
    * @see org.evd.game.ConnService.ConnTestProxy#connTest2()
    */
    public static void connTest2(long actorUniqueId, int a, Object b, org.evd.game.common.serializeBean.ConnInfo connInfo){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.PLAYER, actorUniqueId);
        createMessageLocationSender().send(actorId, EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_COMMON_SERIALIZEBEAN_CONNINFO, new Object[]{a, b, connInfo});
    }
    /**
    * @see org.evd.game.ConnService.ConnTestProxy#connTest3()
    */
    public static void connTest3(long actorUniqueId){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.PLAYER, actorUniqueId);
        createMessageLocationSender().send(actorId, EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST3, new Object[]{});
    }
}
