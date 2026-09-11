package org.evd.game.MatchService.matchType;

import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.entity.result.MatchTeamGroupResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 普通队伍分配算法，匹配规则只属于本子类。 */
public class CommonTeamMatching extends AbsTeamMatching {
    public CommonTeamMatching(int maxRoleNum, boolean pvp, Map<Integer, Integer> dutyLimit) {
        super(maxRoleNum, pvp, dutyLimit);
    }

    public CommonTeamMatching(int maxRoleNum, int maxRobotNum, boolean needRobot,
                              Map<Integer, Integer> dutyLimit, boolean pvp) {
        super(maxRoleNum, maxRobotNum, needRobot, dutyLimit, pvp);
    }

    @Override
    protected MatchTeamGroupResult matchSingleTeam(MatchTeam main,
                                                     Map<Integer, List<MatchTeam>> classified) {
        MatchTeamGroupResult result = matchSingleTeamInternal(main, classified);
        if (result == null) return null;
        if (result.getRoleNum() < maxRoleNum && needRobot) fillRobotTeam(result);
        return result.getRoleNum() == maxRoleNum ? result : null;
    }

    private MatchTeamGroupResult matchSingleTeamInternal(MatchTeam main,
                                                          Map<Integer, List<MatchTeam>> classified) {
        Set<Long> used = new HashSet<>();
        used.add(main.getTeamId());
        MatchTeamGroupResult result = createMatchTeamGroupResult();
        if (!result.checkValid(main)) return null;
        result.addTeams(main);

        int needRoleNum = maxRoleNum - result.getRoleNum();
        for (int size = main.getMemberSize(); size > 0 && needRoleNum > 0; size--) {
            List<MatchTeam> teams = classified.getOrDefault(size, List.of());
            for (MatchTeam team : teams) {
                if (needRoleNum <= 0) break;
                if (size > needRoleNum) break;
                if (used.contains(team.getTeamId())
                        || alreadyMatchTeams.contains(team.getTeamId())) {
                    continue;
                }
                if (!result.checkValid(team)) {
                    continue;
                }
                result.addTeams(team);
                used.add(team.getTeamId());
                needRoleNum -= team.getMemberSize();
            }
        }
        return result;
    }
}
