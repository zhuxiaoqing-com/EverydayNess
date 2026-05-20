package org.evd.game.StageService;

import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcActorType;
import org.evd.game.annotation.RpcRoute;

public class HaHaHaActor {

    @Rpc(route = RpcRoute.LOCATION, actorType = RpcActorType.PLAYER)
    public void rpc1(int a,int b ) {

    }

    @Rpc(route = RpcRoute.LOCATION, actorType = RpcActorType.PLAYER)
    public void rpc2(Object a,Object b) {

    }

    @Rpc()
    public void rpc3(Object a,Object b) {

    }

    @Rpc(route = RpcRoute.LOCATION, actorType = RpcActorType.GUILD)
    public void rpc4(Object a,Object b) {

    }
}
