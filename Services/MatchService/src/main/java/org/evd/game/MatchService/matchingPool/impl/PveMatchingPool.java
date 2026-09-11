package org.evd.game.MatchService.matchingPool.impl;

import org.evd.game.MatchService.entity.pool.MatchDungeonObj;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.matchingPool.TeamMatchingPool;
import org.evd.game.MatchService.entity.result.MatchTeamResultList;
import org.evd.game.MatchService.matchType.CommonTeamMatching;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.util.id.SceneIdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** PVE 匹配池：一边凑满副本人数后创建一张新地图。 */
public class PveMatchingPool extends TeamMatchingPool {
    public PveMatchingPool() { this(org.evd.game.common.constant.MatchType.PVE_MATCH); }

    protected PveMatchingPool(int matchType) { super(matchType); }

    @Override
    protected void matchingDungeon(MatchDungeonObj dungeon) {
        var config = MapConfigs.get(dungeon.getDungeonId());
        if (config == null || config.getPlayerLimit() <= 0) {
            return;
        }
        Map<Long, MatchTeam> teams = dungeon.getTeamMap();
        boolean needRobot = needFillRobot(teams);
        List<MatchTeamResultList> matching = new CommonTeamMatching(
                config.getPlayerLimit(), maxRobotNum(teams, config.getPlayerLimit()), needRobot,
                dutyLimit(dungeon), false).matching(teams);
        for (MatchTeamResultList result : matching) {
            List<MatchTeam> matchedTeams = new ArrayList<>(result.getMatchingTeam().values());
            var matchInfo = new SMapInfo(0L, dungeon.getDungeonId(), SceneIdGenerator.nextId());
            removeMatchAndNotify(matchInfo, matchedTeams);
            createAndEnterMap(matchInfo, matchedTeams, Map.of());
        }
    }
}
