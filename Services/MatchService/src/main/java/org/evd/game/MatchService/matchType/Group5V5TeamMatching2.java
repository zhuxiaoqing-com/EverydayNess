package org.evd.game.MatchService.matchType;

import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.entity.result.MatchTeamGroupResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 第二套分配算法：从主队伍人数向下扫描队伍桶，逐个贪心补齐基础 5 人组。 */
public final class Group5V5TeamMatching2 extends Group5V5TeamMatching {
    public Group5V5TeamMatching2(int maxRoleNum, boolean pvp, Map<Integer, Integer> dutyLimit) {
        super(maxRoleNum, pvp, dutyLimit);
    }

    public Group5V5TeamMatching2(int maxRoleNum, int maxRobotNum, boolean needRobot,
                                 Map<Integer, Integer> dutyLimit, boolean pvp) {
        super(maxRoleNum, maxRobotNum, needRobot, dutyLimit, pvp);
    }

    @Override
    protected MatchTeamGroupResult matchSingleTeam(MatchTeam main,
                                                     Map<Integer, List<MatchTeam>> classified) {
        Set<Long> used = new HashSet<>();
        used.add(main.getTeamId());
        MatchTeamGroupResult result = createMatchTeamGroupResult();
        if (!result.checkValid(main)) return null;
        result.addTeams(main);

        int needRoleNum = getFullMaxRoleNum() - result.getRoleNum();
        for (int size = main.getMemberSize(); size > 0 && needRoleNum > 0; size--) {
            for (MatchTeam team : classified.getOrDefault(size, List.of())) {
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
        if (result.getRoleNum() == getFullMaxRoleNum() || needRobot) return result;
        return null;
    }
}
