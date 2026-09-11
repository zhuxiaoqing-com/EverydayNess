package org.evd.game.MatchService.matchingPool;

import org.evd.game.MatchService.entity.pool.MatchDungeonObj;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.config.MatchRobotConfig;
import java.util.Map;

/** 组队匹配公共算法，保留队伍不可拆分、职责约束和精确凑满语义。 */
public abstract class TeamMatchingPool extends AbstractMatchingPool {
    protected TeamMatchingPool(int matchType) { super(matchType); }

    protected Map<Integer, Integer> dutyLimit(MatchDungeonObj dungeon) {
        return MatchRobotConfig.getDutyLimits(dungeon.getDungeonId());
    }

    protected boolean needFillRobot(Map<Long, MatchTeam> teams) {
        return !teams.isEmpty()
                && MatchRobotConfig.shouldFill(teams.values().stream()
                .mapToLong(MatchTeam::getMatchTime).min().orElse(0L));
    }

    protected int maxRobotNum(Map<Long, MatchTeam> teams, int playerLimit) {
        int roleNum = teams.values().stream().mapToInt(MatchTeam::getMemberSize).sum();
        return MatchRobotConfig.maxRobotNum(matchType, playerLimit, roleNum);
    }
}
