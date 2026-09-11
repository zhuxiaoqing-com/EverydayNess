package org.evd.game.MatchService.entity.pool;

import org.evd.game.MatchService.entity.team.MatchTeam;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 一个副本候选队列。一个队伍可同时出现在多个 MatchDungeonObj 中。 */
public final class MatchDungeonObj {
    private final int dungeonId;
    private final Map<Long, MatchTeam> teamMap = new LinkedHashMap<>();
    private final Map<Integer, MatchDutyObj> dutyMap = new HashMap<>();
    private int roleNum;

    public MatchDungeonObj(int dungeonId) { this.dungeonId = dungeonId; }

    public void addTeam(MatchTeam team) {
        if (teamMap.putIfAbsent(team.getTeamId(), team) != null) return;
        roleNum += team.getMemberSize();
        for (var player : team.getMembers()) {
            for (Integer duty : player.getDutyIds()) {
                dutyMap.computeIfAbsent(duty, MatchDutyObj::new).add(player);
            }
        }
    }

    public boolean removeTeam(MatchTeam team) {
        if (!teamMap.remove(team.getTeamId(), team)) return false;
        roleNum -= team.getMemberSize();
        for (var player : team.getMembers()) {
            for (Integer duty : player.getDutyIds()) {
                MatchDutyObj dutyObj = dutyMap.get(duty);
                if (dutyObj != null) dutyObj.remove(player);
            }
        }
        return true;
    }

    public int getDungeonId() { return dungeonId; }
    public int getRoleNum() { return roleNum; }
    public Map<Long, MatchTeam> getTeamMap() { return teamMap; }
    public Map<Integer, MatchDutyObj> getDutyMap() { return dutyMap; }
}
