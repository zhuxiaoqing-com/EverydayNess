package org.evd.game.StageService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.ClientCmd;
import org.evd.game.annotation.actor.ClientCmdHandler;
import org.evd.game.annotation.actor.ActorType;
import org.evd.game.common.proto.C2S_Login2;
import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.client.ClientSessionRef;

@Actor
@ClientCmdHandler
public class HaHaHaClientCmd {
    @ClientCmd(value = MsgId.C2S_LOGIN3_VALUE, actorType = ActorType.MAP_PLAYER)
    public void client(ClientSessionRef session, C2S_Login2 request) {
    }
}
