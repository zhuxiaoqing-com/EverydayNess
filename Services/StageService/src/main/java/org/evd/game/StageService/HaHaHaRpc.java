package org.evd.game.StageService;


import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.annotation.actor.ActorType;
import org.evd.game.runtime.actor.ActorId;

@Actor
@RpcHandler
public class HaHaHaRpc {

    @Rpc(actorType = ActorType.MAP_PLAYER)
    public void rpc1(ActorId actorId, int a,int b ) {

    }

    @Rpc(actorType = ActorType.MAP_PLAYER)
    public void rpc2(ActorId actorId, Object a,Object b) {
    }

    @Rpc()
    public void rpc3(Object a,Object b) {

    }

    @Rpc(actorType = ActorType.MAP_PLAYER)
    public void rpc4(ActorId actorId, Object a,Object b) {

    }
}
