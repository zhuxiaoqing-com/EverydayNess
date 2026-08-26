package org.evd.game.StageService;


import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.annotation.actor.ActorType;

@Actor
@RpcHandler
public class HaHaHaRpc {

    @Rpc(actorType = ActorType.MAP_PLAYER)
    public void rpc1(int a,int b ) {

    }

    @Rpc(actorType = ActorType.MAP_PLAYER)
    public void rpc2(Object a,Object b) {
    }

    @Rpc()
    public void rpc3(Object a,Object b) {

    }

    @Rpc(actorType = ActorType.MAP_PLAYER)
    public void rpc4(Object a,Object b) {

    }
}
