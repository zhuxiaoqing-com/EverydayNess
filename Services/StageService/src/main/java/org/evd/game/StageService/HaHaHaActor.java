package org.evd.game.StageService;


import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proto.C2S_Login2;
import org.evd.game.common.proto.MsgId;
import org.evd.game.annotation.ActorType;
import org.evd.game.runtime.client.ClientSessionRef;

@Actor
public class HaHaHaActor {

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


    @ClientCmd(value = MsgId.C2S_LOGIN3_VALUE, actorType = ActorType.MAP_PLAYER)
    public void client(ClientSessionRef session, C2S_Login2 c2SLogin2) {

    }
}
