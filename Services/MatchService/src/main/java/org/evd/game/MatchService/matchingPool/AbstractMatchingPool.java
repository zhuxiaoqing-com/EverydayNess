package org.evd.game.MatchService.matchingPool;

import org.evd.game.MatchService.entity.pool.MatchDungeonObj;
import org.evd.game.MatchService.entity.pool.MatchPoolObj;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.MatchLogic;
import org.evd.game.MatchService.MatchSceneLogic;
import org.evd.game.MatchService.MatchService;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 匹配池模板：打乱副本引用顺序后直接使用当前队列，不复制匹配池状态。 */
@SuppressWarnings("DataFlowIssue")
public abstract class AbstractMatchingPool {
    protected final int matchType;
    protected final MatchPoolObj poolObj = new MatchPoolObj();

    protected AbstractMatchingPool(int matchType) {
        this.matchType = matchType;
    }

    public void addTeam(MatchTeam team) { poolObj.addTeam(team); }
    public void removeTeam(MatchTeam team) {
        poolObj.removeTeam(team);
        Service.getCurrent(MatchService.class).getActor(MatchLogic.class)
                .removeMatchedTeams(List.of(team));
    }
    public int getMatchPlayerNum(int mapCfgId) {
        MatchDungeonObj dungeon = poolObj.getMatchDungeonObj(mapCfgId);
        return dungeon == null ? 0 : dungeon.getRoleNum();
    }

    public final void matching() {
        List<MatchDungeonObj> dungeons = new ArrayList<>(poolObj.getDungeonMap().values());
        Collections.shuffle(dungeons);
        for (MatchDungeonObj dungeon : dungeons) {
            if (dungeon.getRoleNum() <= 0) {
                continue;
            }
            matchingDungeon(dungeon);
        }
    }

    protected abstract void matchingDungeon(MatchDungeonObj dungeon);

    protected void removeMatchAndNotify(SMapInfo matchInfo, List<MatchTeam> teams) {
        MatchService service = Service.getCurrent(MatchService.class);
        for (MatchTeam team : teams) {
            removeTeam(team);
        }
        service.getActor(MatchLogic.class).notifyTeamMatchResult(teams, matchType, true, matchInfo);
        service.getActor(MatchLogic.class).clearMatchState(teams);
    }

    protected void createAndEnterMap(SMapInfo matchInfo, List<MatchTeam> teams,
                                     Map<Long, Integer> teamCamps) {
        Service.getCurrent(MatchService.class).getActor(MatchSceneLogic.class)
                .createAndEnterMap(matchInfo, teams, teamCamps);
    }

    public int getMatchType() { return matchType; }
}
