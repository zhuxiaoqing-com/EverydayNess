package org.evd.game.MatchService.matchingPool.impl;

import org.evd.game.MatchService.MatchSceneLogic;
import org.evd.game.MatchService.MatchService;
import org.evd.game.MatchService.entity.pool.MatchDungeonObj;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.matchingPool.TeamMatchingPool;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.runtime.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 房间匹配池：根据房间两边剩余容量追加真实队伍，复用原 groupId。 */
public final class RoomMatchingPool extends TeamMatchingPool {
    public RoomMatchingPool() {
        super(org.evd.game.common.constant.MatchType.ROOM_MATCH);
    }

    @Override
    protected void matchingDungeon(MatchDungeonObj dungeon) {
        MatchService service = Service.getCurrent(MatchService.class);
        MatchSceneLogic sceneLogic = service.getActor(MatchSceneLogic.class);
        List<SRunningMapInfo> runningMaps = sceneLogic.getRunningMaps(dungeon.getDungeonId());
        for (SRunningMapInfo runningMap : runningMaps) {
            int hostNeed = runningMap.getSideLimit() - runningMap.getHostRoleNum();
            int guestNeed = runningMap.getSideLimit() - runningMap.getGuestRoleNum();
            if (hostNeed <= 0 && guestNeed <= 0) {
                continue;
            }
            Map<MatchTeam, Integer> selected = new LinkedHashMap<>();
            List<MatchTeam> candidates = new ArrayList<>(dungeon.getTeamMap().values());
            candidates.sort(Comparator.comparingLong(MatchTeam::getMatchTime));
            for (MatchTeam team : candidates) {
                int camp = hostNeed >= guestNeed ? 1 : 2;
                if (camp == 1 && team.getMemberSize() > hostNeed) {
                    continue;
                }
                if (camp == 2 && team.getMemberSize() > guestNeed) {
                    continue;
                }
                if (camp == 1) {
                    hostNeed -= team.getMemberSize();
                } else {
                    guestNeed -= team.getMemberSize();
                }
                selected.put(team, camp);
            }
            if (selected.isEmpty()) {
                continue;
            }
            Map<Long, Integer> camps = new LinkedHashMap<>();
            selected.forEach((team, camp) -> camps.put(team.getTeamId(), camp));
            var mapInfo = runningMap.getMapInfo();
            List<MatchTeam> matchedTeams = new ArrayList<>(selected.keySet());
            removeMatchAndNotify(mapInfo, matchedTeams);
            sceneLogic.dispatchMatch(mapInfo, matchedTeams, camps);
        }
    }
}
