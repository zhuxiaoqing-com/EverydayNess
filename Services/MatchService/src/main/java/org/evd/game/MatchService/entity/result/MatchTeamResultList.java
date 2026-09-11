package org.evd.game.MatchService.entity.result;

import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.entity.result.MatchTeamGroupResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PVE 一场结果或 PVP 的一方结果集合。 */
public final class MatchTeamResultList {
    private final List<MatchTeamGroupResult> resultList = new ArrayList<>();
    private int roleNum;

    public MatchTeamResultList() {
    }

    public MatchTeamResultList(MatchTeamGroupResult result) {
        add(result);
    }

    public MatchTeamResultList(MatchTeamResultList other) {
        for (MatchTeamGroupResult result : other.resultList) {
            add(result);
        }
    }

    public void add(MatchTeamGroupResult result) {
        resultList.add(result);
        roleNum += result.getRoleNum();
    }

    public void clear() {
        resultList.clear();
        roleNum = 0;
    }

    public Map<Long, MatchTeam> getMatchingTeam() {
        Map<Long, MatchTeam> result = new HashMap<>();
        for (MatchTeamGroupResult group : resultList) {
            result.putAll(group.getTeams());
        }
        return result;
    }

    public MatchTeamGroupResult toMatchTeamGroupResult() {
        MatchTeamGroupResult result = new MatchTeamGroupResult();
        for (MatchTeamGroupResult group : resultList) {
            result.merge(group);
        }
        return result;
    }

    public MatchTeamResultList copy() {
        return new MatchTeamResultList(this);
    }

    public List<MatchTeamGroupResult> getResultList() { return resultList; }
    public int getRoleNum() { return roleNum; }
    public int getRealRoleNum() {
        return resultList.stream().mapToInt(MatchTeamGroupResult::getRealRoleNum).sum();
    }
    public int getRoleRobotNum() { return roleNum; }
    public int getRobotNum() {
        return resultList.stream().mapToInt(MatchTeamGroupResult::getRobotNum).sum();
    }
    public int getRealRoleTeamNum() {
        return resultList.stream().mapToInt(MatchTeamGroupResult::getRealRoleTeamNum).sum();
    }
}
