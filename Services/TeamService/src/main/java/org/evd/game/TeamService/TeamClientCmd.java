package org.evd.game.TeamService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.ClientCmd;
import org.evd.game.annotation.actor.ClientCmdHandler;
import org.evd.game.common.proto.C2S_TeamAccept;
import org.evd.game.common.proto.C2S_TeamCancelMatch;
import org.evd.game.common.proto.C2S_TeamCreate;
import org.evd.game.common.proto.C2S_TeamDisband;
import org.evd.game.common.proto.C2S_TeamInvite;
import org.evd.game.common.proto.C2S_TeamKick;
import org.evd.game.common.proto.C2S_TeamLeave;
import org.evd.game.common.proto.C2S_TeamMatch;
import org.evd.game.common.proto.TeamMsgId;
import org.evd.game.common.serializeBean.TeamService.team.STeamMatchRequest;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;

/** TeamService 客户端命令入口；队伍状态和业务规则直接由 TeamLogic 维护。 */
@Actor
@ClientCmdHandler
@SuppressWarnings("unused")
public final class TeamClientCmd {
    @ClientCmd(TeamMsgId.C2S_TEAM_CREATE_VALUE)
    public void create(ClientSessionRef session, C2S_TeamCreate request) {
        logic().create(session.getPlayerId(), playerService(session.getPlayerId()));
    }

    @ClientCmd(TeamMsgId.C2S_TEAM_INVITE_VALUE)
    public void invite(ClientSessionRef session, C2S_TeamInvite request) {
        logic().invite(session.getPlayerId(), request.getPlayerId());
    }

    @ClientCmd(TeamMsgId.C2S_TEAM_ACCEPT_VALUE)
    public void accept(ClientSessionRef session, C2S_TeamAccept request) {
        logic().accept(session.getPlayerId(), request.getTeamId(), playerService(session.getPlayerId()));
    }

    @ClientCmd(TeamMsgId.C2S_TEAM_LEAVE_VALUE)
    public void leave(ClientSessionRef session, C2S_TeamLeave request) {
        logic().leave(session.getPlayerId());
    }

    @ClientCmd(TeamMsgId.C2S_TEAM_KICK_VALUE)
    public void kick(ClientSessionRef session, C2S_TeamKick request) {
        logic().kick(session.getPlayerId(), request.getPlayerId());
    }

    @ClientCmd(TeamMsgId.C2S_TEAM_DISBAND_VALUE)
    public void disband(ClientSessionRef session, C2S_TeamDisband request) {
        logic().disband(session.getPlayerId());
    }

    @ClientCmd(TeamMsgId.C2S_TEAM_MATCH_VALUE)
    public void match(ClientSessionRef session, C2S_TeamMatch request) {
        STeamMatchRequest matchRequest = new STeamMatchRequest(
                request.getMatchType(), request.getMapCfgIdsList(), request.getDutyIdsList());
        logic().match(session.getPlayerId(), matchRequest);
    }

    @ClientCmd(TeamMsgId.C2S_TEAM_CANCEL_MATCH_VALUE)
    public void cancelMatch(ClientSessionRef session, C2S_TeamCancelMatch request) {
        logic().cancelMatch(session.getPlayerId());
    }

    private TeamLogic logic() {
        return Service.getCurrent(TeamService.class).getActor(TeamLogic.class);
    }

    private CallPoint playerService(long playerId) {
        ActorAddress playerActor = Service.getCurrent(TeamService.class)
                .getMessageLocationSender().getOrQuery(ActorId.player(playerId));
        if (playerActor == null || playerActor.getCallPoint() == null) {
            throw new IllegalStateException("找不到玩家所属 PlayerService: playerId=" + playerId);
        }
        return playerActor.getCallPoint();
    }
}
