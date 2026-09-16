package org.evd.game.TeamService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;

/** 组队匹配结果处理入口，统一收敛 MatchService 的异步回调。 */
@Actor
public final class TeamMatchLogic {
    public boolean cancelMatch(long teamId) {
        return org.evd.game.runtime.Service.getCurrent(TeamService.class)
                .getActor(TeamLogic.class).cancelMatchState(teamId);
    }

    public void matchResult(long teamId, int matchType, boolean success, SMapInfo mapInfo) {
        TeamLogic logic = org.evd.game.runtime.Service.getCurrent(TeamService.class)
                .getActor(TeamLogic.class);
        logic.matchResult(teamId, matchType, success, mapInfo);
    }
}
