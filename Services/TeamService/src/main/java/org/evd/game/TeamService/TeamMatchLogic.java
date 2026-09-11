package org.evd.game.TeamService;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;

/** 组队匹配结果处理入口。 */
@Slf4j
@Actor
public final class TeamMatchLogic {
    public void matchResult(long teamId, int matchType, boolean success, SMapInfo mapInfo) {
        log.info("TeamService 组队匹配结果: teamId={}, matchType={}, success={}, mapInfo={}",
                teamId, matchType, success, mapInfo);
    }
}
