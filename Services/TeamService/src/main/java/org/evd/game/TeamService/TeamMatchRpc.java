package org.evd.game.TeamService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;

/** TeamService 组队匹配结果 RPC 入口。 */
@Actor
@RpcHandler
public final class TeamMatchRpc {
    /** MatchService 主动取消队伍匹配时，只清理 TeamLogic 的匹配状态。 */
    @Rpc
    public boolean cancelMatch(long teamId) {
        return Service.getCurrent(TeamService.class).getActor(TeamMatchLogic.class)
                .cancelMatch(teamId);
    }

    @Rpc
    public void matchResult(long teamId, int matchType, boolean success, SMapInfo mapInfo) {
        Service.getCurrent(TeamService.class).getActor(TeamMatchLogic.class)
                .matchResult(teamId, matchType, success, mapInfo);
    }
}
