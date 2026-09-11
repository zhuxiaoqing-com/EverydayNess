package org.evd.game.MatchService.matchType;

import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.entity.result.MatchTeamGroupResult;
import org.evd.game.MatchService.entity.result.MatchTeamResultList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 第一套分配算法：固定组合组成基础 5 人组，再拼成大场次。 */
public class Group5V5TeamMatching extends AbsTeamMatching {

    /**
     * 这种方式有个好处就是 2,3模式下，可以挑一个自己最符合的; 如果是贪心遍历的话，会221没有的情况下，才会进行111;
     * 或者如果10人队的话，可能会出现，比如631 和622; 当631匹配不到人的时候，永远也适配不到622
     */
    private static final Map<Integer, List<List<Integer>>> TEAM_PATTERNS = Map.of(
            1, List.of(List.of(1, 1, 1, 1)),
            2, List.of(List.of(2, 1), List.of(1, 1, 1)),
            3, List.of(List.of(2), List.of(1, 1)),
            4, List.of(List.of(1)),
            5, List.of(List.of())
    );

    public Group5V5TeamMatching(int maxRoleNum, boolean pvp, Map<Integer, Integer> dutyLimit) {
        super(maxRoleNum, pvp, dutyLimit);
    }

    public Group5V5TeamMatching(int maxRoleNum, int maxRobotNum, boolean needRobot,
                                Map<Integer, Integer> dutyLimit, boolean pvp) {
        super(maxRoleNum, maxRobotNum, needRobot, dutyLimit, pvp);
    }

    @Override
    protected MatchTeamGroupResult matchSingleTeam(MatchTeam main,
                                                     Map<Integer, List<MatchTeam>> classified) {
        List<List<Integer>> patterns = TEAM_PATTERNS.getOrDefault(main.getMemberSize(), List.of());
        MatchTeamGroupResult best = null;
        for (List<Integer> pattern : patterns) {
            MatchTeamGroupResult current = matchSingleTeamInternal(main, classified, pattern);
            if (current != null && (best == null || current.getRoleNum() > best.getRoleNum())) {
                best = current;
            }
        }
        if (best != null && best.getRoleNum() < getFullMaxRoleNum() && needRobot) {
            fillRobotTeam(best);
        }
        return best != null && best.getRoleNum() == getFullMaxRoleNum() ? best : null;
    }

    private MatchTeamGroupResult matchSingleTeamInternal(MatchTeam main,
                                                          Map<Integer, List<MatchTeam>> classified,
                                                          List<Integer> pattern) {
        Set<Long> used = new HashSet<>();
        used.add(main.getTeamId());
        MatchTeamGroupResult result = createMatchTeamGroupResult();
        if (!result.checkValid(main)) return null;
        result.addTeams(main);
        for (Integer size : pattern) {
            MatchTeam selected = classified.getOrDefault(size, List.<MatchTeam>of()).stream()
                    .filter(team -> !used.contains(team.getTeamId()))
                    .filter(team -> !alreadyMatchTeams.contains(team.getTeamId()))
                    .filter(result::checkValid)
                    .findFirst().orElse(null);
            if (selected == null) {
                if (needRobot) {
                    continue;
                }
                return null;
            }
            result.addTeams(selected);
            used.add(selected.getTeamId());
        }
        if (result.getRoleNum() == getFullMaxRoleNum() || needRobot) return result;
        return null;
    }

    @Override
    protected List<MatchTeamResultList> toMatchTeamResultList(List<MatchTeamGroupResult> groups) {
        if (maxRoleNum < getFullMaxRoleNum() || maxRoleNum % getFullMaxRoleNum() != 0) {
            return List.of();
        }
        int ratio = maxRoleNum / getFullMaxRoleNum();
        List<MatchTeamResultList> result = new ArrayList<>();
        for (int i = 0; i < groups.size(); i += ratio) {
            MatchTeamResultList current = new MatchTeamResultList();
            for (int j = i; j < i + ratio && j < groups.size(); j++) {
                current.add(groups.get(j));
            }
            if (current.getRoleNum() == maxRoleNum) result.add(current);
        }
        return result;
    }

    public int getFullMaxRoleNum() {
        return MAX_TEAM_NUM;
    }
}
