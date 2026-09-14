package org.evd.game.PlayerService.map.match;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.ActorType;
import org.evd.game.annotation.actor.ClientCmd;
import org.evd.game.annotation.actor.ClientCmdHandler;
import org.evd.game.common.proto.C2S_CancelMatch;
import org.evd.game.common.proto.C2S_Match;
import org.evd.game.common.proto.MatchMsgId;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;

/** 玩家客户端的匹配开始和取消命令入口。 */
@Actor
@ClientCmdHandler
public final class PlayerMatchClientCmd {
    @ClientCmd(value = MatchMsgId.C2S_MATCH_START_VALUE, actorType = ActorType.PLAYER)
    public void match(ClientSessionRef session, C2S_Match request) {
        Service.getCurrent(PlayerService.class).getActor(PlayerMatchLogic.class)
                .startSingleMatch(session, request);
    }

    @ClientCmd(value = MatchMsgId.C2S_MATCH_CANCEL_VALUE, actorType = ActorType.PLAYER)
    public void cancelMatch(ClientSessionRef session, C2S_CancelMatch request) {
        Service.getCurrent(PlayerService.class).getActor(PlayerMatchLogic.class)
                .cancelMatch(session);
    }
}
