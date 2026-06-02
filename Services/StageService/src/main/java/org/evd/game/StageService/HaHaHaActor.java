package org.evd.game.StageService;

import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcActorType;
import org.evd.game.annotation.RpcRoute;
import org.evd.game.common.proto.C2S_Login2;
import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.client.ClientSessionRef;

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


    @ClientCmd(MsgId.C2S_LOGIN3_VALUE)
    public void client(ClientSessionRef session, C2S_Login2 c2SLogin2) {

    }
}
