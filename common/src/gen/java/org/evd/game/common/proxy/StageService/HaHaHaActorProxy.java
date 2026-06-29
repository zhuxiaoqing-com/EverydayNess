package org.evd.game.common.proxy.StageService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.annotation.ActorType;

/**
* 根据HaHaHaActorService生成的代理类
*/
public final class HaHaHaActorProxy {

    private static final HaHaHaActorProxy INSTANCE = new HaHaHaActorProxy();

    private HaHaHaActorProxy() {
    }

    public static HaHaHaActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_HAHAHAACTOR_RPC1_0 = 0;
        public final static int ENUM_HAHAHAACTOR_RPC2_1 = 1;
        public final static int ENUM_HAHAHAACTOR_RPC3_2 = 2;
        public final static int ENUM_HAHAHAACTOR_RPC4_3 = 3;
    }

    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaActor#rpc1()
    */
    public void rpc1(long actorUniqueId, int a, int b){
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_RPC1_0, new Object[]{a, b});
    }


    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaActor#rpc2()
    */
    public void rpc2(long actorUniqueId, Object a, Object b){
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_RPC2_1, new Object[]{a, b});
    }


    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaActor#rpc3()
    */
    public void rpc3(CallPoint remote, Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_HAHAHAACTOR_RPC3_2, new Object[]{a, b});
    }


    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaActor#rpc4()
    */
    public void rpc4(long actorUniqueId, Object a, Object b){
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_RPC4_3, new Object[]{a, b});
    }


}
