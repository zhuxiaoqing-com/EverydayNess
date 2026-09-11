package org.evd.game.MatchService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRequest;
import org.evd.game.common.serializeBean.MatchService.match.SMatchTeamRequest;
import org.evd.game.runtime.Service;

/** MatchService 的匹配提交、取消和查询 RPC 入口。 */
@Actor
@RpcHandler
public final class MatchRpc {
    @Rpc
    public boolean match(SMatchRequest request) {
        return logic().match(request);
    }

    @Rpc
    public boolean teamMatch(SMatchTeamRequest request) {
        return logic().teamMatch(request);
    }

    @Rpc
    public boolean cancel(long playerId) {
        return logic().cancel(playerId);
    }

    @Rpc
    public boolean cancelTeam(long teamId) {
        return logic().cancelTeam(teamId);
    }

    @Rpc
    public int getMatchPlayerNum(int matchType, int mapCfgId) {
        return logic().getMatchPlayerNum(matchType, mapCfgId);
    }

    private MatchLogic logic() {
        return Service.getCurrent(MatchService.class).getActor(MatchLogic.class);
    }
}
