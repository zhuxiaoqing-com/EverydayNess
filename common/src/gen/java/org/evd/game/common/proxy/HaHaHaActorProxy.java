package org.evd.game.common.proxy;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.location.MessageLocationSender;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorType;

/**
* 根据HaHaHaActorService生成的代理类
*/
public final class HaHaHaActorProxy {

    private HaHaHaActorProxy() {
    }

    private static MessageLocationSender createMessageLocationSender() {
        return new MessageLocationSender(HaHaHaActorProxy::queryActorAddress);
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
        public final static int ENUM_HAHAHAACTOR_RPC1_5 = 5;
        public final static int ENUM_HAHAHAACTOR_RPC2_6 = 6;
        public final static int ENUM_HAHAHAACTOR_RPC3_7 = 7;
        public final static int ENUM_HAHAHAACTOR_RPC4_8 = 8;
    }

    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc1()
    */
    public static void rpc1(long actorUniqueId, int a, int b){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        createMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_RPC1_5, new Object[]{a, b});
    }


    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc2()
    */
    public static void rpc2(long actorUniqueId, Object a, Object b){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        createMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_RPC2_6, new Object[]{a, b});
    }


    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc3()
    */
    public static void rpc3(CallPoint remote, Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_HAHAHAACTOR_RPC3_7, new Object[]{a, b});
    }


    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc4()
    */
    public static void rpc4(long actorUniqueId, Object a, Object b){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        createMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_RPC4_8, new Object[]{a, b});
    }


}
