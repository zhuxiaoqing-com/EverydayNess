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

    public final static class EnumCall{
        public final static int ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT = 6;
        public final static int ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT = 7;
        public final static int ENUM_HAHAHAACTOR_VOID_RPC3_OBJECT_OBJECT = 8;
        public final static int ENUM_HAHAHAACTOR_VOID_RPC4_OBJECT_OBJECT = 9;
    }

    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc1()
    */
    public static void rpc1(long actorUniqueId, int a, int b){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.PLAYER, actorUniqueId);
        new MessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT, new Object[]{a, b});
    }
    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc2()
    */
    public static void rpc2(long actorUniqueId, Object a, Object b){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.PLAYER, actorUniqueId);
        new MessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT, new Object[]{a, b});
    }
    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc3()
    */
    public static void rpc3(CallPoint remote, Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC3_OBJECT_OBJECT, new Object[]{a, b});
    }
    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc4()
    */
    public static void rpc4(long actorUniqueId, Object a, Object b){
        Service service = Service.getCurrent();
        ActorId actorId = new ActorId(ActorType.GUILD, actorUniqueId);
        new MessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC4_OBJECT_OBJECT, new Object[]{a, b});
    }
}
