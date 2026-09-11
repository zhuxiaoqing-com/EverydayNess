package org.evd.game.MatchService.matchType;

import org.evd.game.MatchService.entity.result.MatchDutyTeamGroupResult;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.entity.result.MatchTeamGroupResult;
import org.evd.game.MatchService.entity.result.MatchTeamResultList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 匹配公共流程；具体队伍如何分配由 CommonTeamMatching、Group5V5TeamMatching
 * 和 Group5V5TeamMatching2 分别实现。
 */
public abstract class AbsTeamMatching {
    protected static final int MAX_TEAM_NUM = 5;
    protected final boolean needRobot;
    protected final int maxRobotNum;
    protected final int maxRoleNum;
    protected final boolean pvp;
    protected final Map<Integer, Integer> dutyMap;
    protected final Set<Long> alreadyMatchTeams = new HashSet<>();

    protected AbsTeamMatching(int maxRoleNum, boolean pvp, Map<Integer, Integer> dutyMap) {
        this(maxRoleNum, 0, false, dutyMap, pvp);
    }

    protected AbsTeamMatching(int maxRoleNum, int maxRobotNum, boolean needRobot,
                              Map<Integer, Integer> dutyMap, boolean pvp) {
        this.maxRoleNum = maxRoleNum;
        this.needRobot = needRobot;
        this.maxRobotNum = needRobot ? maxRobotNum : 0;
        this.dutyMap = dutyMap == null ? Map.of() : Map.copyOf(dutyMap);
        this.pvp = pvp;
    }

    public List<MatchTeamResultList> matching(Map<Long, MatchTeam> teamMap) {
        if (teamMap == null || teamMap.isEmpty()) return Collections.emptyList();
        int sum = teamMap.values().stream().mapToInt(MatchTeam::getMemberSize).sum();
        int checkMaxRoleNum = pvp ? maxRoleNum * 2 : maxRoleNum;
        if (checkMaxRoleNum - sum > maxRobotNum) return Collections.emptyList();

        Map<Integer, List<MatchTeam>> classified = classifyTeams(teamMap);
        if (classified.isEmpty()) return Collections.emptyList();
        List<MatchTeamGroupResult> groups = matchAllTeam(classified);
        List<MatchTeamResultList> result = toMatchTeamResultList(groups);
        pvpTeamProcess(result);
        removeByMaxRobotNum(result);
        return result;
    }

    /** 子类只实现这一处：给定主队伍如何从分类桶中找队友。 */
    protected abstract MatchTeamGroupResult matchSingleTeam(
            MatchTeam team, Map<Integer, List<MatchTeam>> classified);

    /** 公共的 5 人到 1 人主队伍遍历和去重流程。 */
    protected List<MatchTeamGroupResult> matchAllTeam(Map<Integer, List<MatchTeam>> classified) {
        List<MatchTeamGroupResult> result = new ArrayList<>();
        for (int size = MAX_TEAM_NUM; size > 0; size--) {
            if (size > maxRoleNum) {
                continue;
            }
            for (MatchTeam team : classified.getOrDefault(size, Collections.emptyList())) {
                if (alreadyMatchTeams.contains(team.getTeamId())) {
                    continue;
                }
                MatchTeamGroupResult group = matchSingleTeam(team, classified);
                if (group == null) {
                    continue;
                }
                result.add(group);
                alreadyMatchTeams.addAll(group.getTeams().keySet());
            }
        }
        return result;
    }

    protected List<MatchTeamResultList> toMatchTeamResultList(List<MatchTeamGroupResult> groups) {
        return groups.stream().map(MatchTeamResultList::new)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    protected MatchTeamGroupResult createMatchTeamGroupResult() {
        return dutyMap.isEmpty() ? new MatchTeamGroupResult()
                : new MatchDutyTeamGroupResult(needRobot, dutyMap);
    }

    protected void fillRobotTeam(MatchTeamGroupResult result) {
        if (result instanceof MatchDutyTeamGroupResult dutyResult) {
            dutyResult.fillRobotTeam(getFullMaxRoleNum());
            return;
        }
        result.fillRobotTeam(getFullMaxRoleNum());
    }

    public int getFullMaxRoleNum() { return maxRoleNum; }
    public Map<Integer, Integer> getDutyMap() { return dutyMap; }

    public static Map<Integer, List<MatchTeam>> classifyTeams(Map<Long, MatchTeam> teamMap) {
        Map<Integer, List<MatchTeam>> result = new HashMap<>();
        for (MatchTeam team : teamMap.values()) {
            result.computeIfAbsent(team.getMemberSize(), ignored -> new ArrayList<>()).add(team);
        }
        for (List<MatchTeam> teams : result.values()) {
            Collections.shuffle(teams);
        }
        return result;
    }

    private void removeByMaxRobotNum(List<MatchTeamResultList> results) {
        if (!needRobot) return;
        int step = pvp ? 2 : 1;
        for (int i = 0; i + step - 1 < results.size(); i += step) {
            int robotNum = results.get(i).getRobotNum();
            if (pvp) robotNum += results.get(i + 1).getRobotNum();
            if (robotNum > maxRobotNum) {
                results.subList(i, i + step).clear();
                i -= step;
            }
        }
    }

    /** PVP 结果两两配对，并按基础组人数重新平均分配两边。 */
    protected void pvpTeamProcess(List<MatchTeamResultList> results) {
        if (!pvp) return;
        if ((results.size() & 1) != 0) {
            if (!needRobot) {
                results.remove(results.size() - 1);
            } else {
                MatchTeamResultList robotSide = new MatchTeamResultList();
                int ratio = maxRoleNum / getFullMaxRoleNum();
                for (int i = 0; i < ratio; i++) {
                    MatchTeamGroupResult robotGroup = createMatchTeamGroupResult();
                    fillRobotTeam(robotGroup);
                    robotSide.add(robotGroup);
                }
                results.add(robotSide);
            }
        }
        if (results.size() < 2) return;
        results.sort(Comparator.comparingInt(MatchTeamResultList::getRealRoleNum).reversed());
        for (int i = 0; i + 1 < results.size(); i += 2) {
            pvpTeamListAverage(results.get(i), results.get(i + 1));
        }
        secondPvpTeamListAverage(results.get(results.size() - 2), results.get(results.size() - 1));
    }

    protected boolean pvpTeamListAverage(MatchTeamResultList left, MatchTeamResultList right) {
        MatchTeamGroupResult newLeft = new MatchTeamGroupResult();
        MatchTeamGroupResult newRight = new MatchTeamGroupResult();
        List<MatchTeamGroupResult> groups = Stream.concat(left.getResultList().stream(),
                        right.getResultList().stream())
                .sorted(Comparator.comparingInt(MatchTeamGroupResult::getRealRoleNum).reversed()
                        .thenComparingInt(MatchTeamGroupResult::getRealRoleTeamNum))
                .toList();
        for (MatchTeamGroupResult group : groups) {
            if (newLeft.getRoleNum() <= newRight.getRoleNum()) newLeft.merge(group);
            else newRight.merge(group);
        }
        left.clear();
        right.clear();
        left.add(newLeft);
        right.add(newRight);
        return true;
    }

    protected boolean secondPvpTeamListAverage(MatchTeamResultList left,
                                               MatchTeamResultList right) {
        if (Math.abs(left.getRealRoleNum() - right.getRealRoleNum()) < 2) return false;
        MatchTeamResultList realSide = left.getRealRoleNum() > right.getRealRoleNum() ? left : right;
        MatchTeamResultList robotSide = realSide == left ? right : left;
        int canSwapRobotNum = Math.min(robotSide.getRobotNum(),
                Math.abs(left.getRealRoleNum() - right.getRealRoleNum()) / 2);
        if (canSwapRobotNum <= 0) return false;

        MatchTeamGroupResult realResult = realSide.toMatchTeamGroupResult();
        MatchTeamGroupResult robotResult = robotSide.toMatchTeamGroupResult();
        int initialCanSwapRobotNum = canSwapRobotNum;
        List<MatchTeam> realTeams = realResult.getTeamList().stream()
                .filter(team -> !team.isRobot())
                .filter(team -> team.getMemberSize() <= initialCanSwapRobotNum)
                .sorted(Comparator.comparingInt(MatchTeam::getMemberSize).reversed())
                .toList();
        for (MatchTeam realTeam : realTeams) {
            if (realTeam.getMemberSize() > canSwapRobotNum) {
                continue;
            }
            realResult.removeTeam(realTeam.getTeamId());
            robotResult.addTeams(realTeam);
            canSwapRobotNum -= realTeam.getMemberSize();
            List<MatchTeam> robotTeams = robotResult.getTeamList().stream()
                    .filter(MatchTeam::isRobot)
                    .limit(realTeam.getMemberSize())
                    .toList();
            for (MatchTeam robotTeam : robotTeams) {
                robotResult.removeTeam(robotTeam.getTeamId());
                realResult.addTeams(robotTeam);
            }
            if (canSwapRobotNum <= 0) break;
        }
        realSide.clear();
        robotSide.clear();
        realSide.add(realResult);
        robotSide.add(robotResult);
        return true;
    }
}
