package org.evd.game.MatchService.entity.result;

import org.evd.game.MatchService.entity.team.MatchTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 一方匹配结果，保存不可拆分的队伍并统计人数。 */
public class MatchTeamGroupResult {
    protected final Map<Long, MatchTeam> teams = new HashMap<>();
    protected int roleNum;
    protected int realRoleNum;
    protected int robotNum;
    protected int realRoleTeamNum;

    public boolean checkValid(MatchTeam team) {
        return team != null && team.getMemberSize() > 0;
    }

    public void addTeams(MatchTeam team) {
        if (team == null || team.getMemberSize() <= 0
                || teams.putIfAbsent(team.getTeamId(), team) != null) return;
        roleNum += team.getMemberSize();
        if (team.isRobot()) {
            robotNum += team.getMemberSize();
        } else {
            realRoleNum += team.getMemberSize();
            realRoleTeamNum++;
        }
    }

    public MatchTeam removeTeam(long teamId) {
        MatchTeam team = teams.remove(teamId);
        if (team != null) {
            roleNum -= team.getMemberSize();
            if (team.isRobot()) {
                robotNum -= team.getMemberSize();
            } else {
                realRoleNum -= team.getMemberSize();
                realRoleTeamNum--;
            }
        }
        return team;
    }

    public void merge(MatchTeamGroupResult result) {
        for (MatchTeam team : result.teams.values()) {
            addTeams(team);
        }
    }

    public Map<Long, MatchTeam> getTeams() { return teams; }
    public int getRoleNum() { return roleNum; }
    public int getRealRoleNum() { return realRoleNum; }
    public int getRobotNum() { return robotNum; }
    public int getRealRoleTeamNum() { return realRoleTeamNum; }
    public List<MatchTeam> getTeamList() { return new ArrayList<>(teams.values()); }

    public void fillRobotTeam(int maxRoleNum) {
        while (roleNum < maxRoleNum) addTeams(MatchTeam.robot(0));
    }
}
