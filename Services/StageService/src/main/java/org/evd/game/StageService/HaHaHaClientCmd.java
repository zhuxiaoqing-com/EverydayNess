package org.evd.game.StageService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.ClientCmdHandler;
import org.evd.game.annotation.ActorType;
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
