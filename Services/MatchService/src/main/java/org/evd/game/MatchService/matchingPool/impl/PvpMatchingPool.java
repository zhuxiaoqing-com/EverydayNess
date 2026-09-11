package org.evd.game.MatchService.matchingPool.impl;

import org.evd.game.MatchService.entity.pool.MatchDungeonObj;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.entity.result.MatchTeamResultList;
import org.evd.game.MatchService.matchingPool.TeamMatchingPool;
import org.evd.game.MatchService.matchType.AbsTeamMatching;
import org.evd.game.MatchService.matchType.CommonTeamMatching;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.util.id.SceneIdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 普通 PVP：两边按副本总人数的一半分别精确组队。 */
public class PvpMatchingPool extends TeamMatchingPool {
    public PvpMatchingPool() { this(org.evd.game.common.constant.MatchType.PVP_MATCH); }

    protected PvpMatchingPool(int matchType) { super(matchType); }

    @Override
    protected void matchingDungeon(MatchDungeonObj dungeon) {
        var config = MapConfigs.get(dungeon.getDungeonId());
        if (config == null || config.getPlayerLimit() <= 0
                || (config.getPlayerLimit() & 1) != 0) {
            return;
        }
        Map<Long, MatchTeam> teams = dungeon.getTeamMap();
        int sideLimit = config.getPlayerLimit() / 2;
        boolean needRobot = needFillRobot(teams);
        List<MatchTeamResultList> matching = createMatcher(sideLimit,
                maxRobotNum(teams, config.getPlayerLimit()), needRobot, dutyLimit(dungeon))
                .matching(teams);
        for (int i = 0; i + 1 < matching.size(); i += 2) {
            List<MatchTeam> host = new ArrayList<>(matching.get(i).getMatchingTeam().values());
            List<MatchTeam> guest = new ArrayList<>(matching.get(i + 1).getMatchingTeam().values());
            List<MatchTeam> matchedTeams = new ArrayList<>(host);
            matchedTeams.addAll(guest);
            var matchInfo = new SMapInfo(0L, dungeon.getDungeonId(), SceneIdGenerator.nextId());
            removeMatchAndNotify(matchInfo, matchedTeams);
            createAndEnterMap(matchInfo, matchedTeams, camps(host, guest));
        }
    }

    protected AbsTeamMatching createMatcher(int sideLimit, int maxRobotNum, boolean needRobot,
                                            java.util.Map<Integer, Integer> dutyLimit) {
        return new CommonTeamMatching(sideLimit, maxRobotNum, needRobot, dutyLimit, true);
    }

    private static java.util.Map<Long, Integer> camps(List<MatchTeam> host, List<MatchTeam> guest) {
        java.util.Map<Long, Integer> result = new java.util.HashMap<>();
        host.forEach(team -> result.put(team.getTeamId(), 1));
        guest.forEach(team -> result.put(team.getTeamId(), 2));
        return result;
    }
}
