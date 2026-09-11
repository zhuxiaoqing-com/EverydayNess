package org.evd.game.MatchService.entity.pool;

import org.evd.game.MatchService.entity.team.MatchTeam;

import java.util.HashMap;
import java.util.Map;

/** 活动级匹配池，按副本保存队伍。 */
public final class MatchPoolObj {
    private final Map<Integer, MatchDungeonObj> dungeonMap = new HashMap<>();
    private int roleNum;

    public void addTeam(MatchTeam team) {
        roleNum += team.getMemberSize();
        for (Integer mapCfgId : team.getMapCfgIds()) {
            dungeonMap.computeIfAbsent(mapCfgId, MatchDungeonObj::new).addTeam(team);
        }
    }

    public void removeTeam(MatchTeam team) {
        boolean removed = false;
        for (Integer mapCfgId : team.getMapCfgIds()) {
            MatchDungeonObj dungeon = dungeonMap.get(mapCfgId);
            if (dungeon != null) {
                removed |= dungeon.removeTeam(team);
                if (dungeon.getTeamMap().isEmpty()) dungeonMap.remove(mapCfgId);
            }
        }
        if (removed) roleNum -= team.getMemberSize();
    }

    public MatchDungeonObj getMatchDungeonObj(int dungeonId) { return dungeonMap.get(dungeonId); }
    public Map<Integer, MatchDungeonObj> getDungeonMap() { return dungeonMap; }
    public int getRoleNum() { return roleNum; }
}
