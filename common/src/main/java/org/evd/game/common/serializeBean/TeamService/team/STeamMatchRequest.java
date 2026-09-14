package org.evd.game.common.serializeBean.TeamService.team;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.ArrayList;
import java.util.List;

/** 队长发起组队匹配时提交的匹配参数。 */
@SerializeClass
public final class STeamMatchRequest implements ISerializable {
    private int matchType;
    private List<Integer> mapCfgIds = new ArrayList<>();
    private List<Integer> dutyIds = new ArrayList<>();

    public STeamMatchRequest() {
    }

    public STeamMatchRequest(int matchType, List<Integer> mapCfgIds, List<Integer> dutyIds) {
        this.matchType = matchType;
        setMapCfgIds(mapCfgIds);
        setDutyIds(dutyIds);
    }

    public int getMatchType() {
        return matchType;
    }

    public void setMatchType(int matchType) {
        this.matchType = matchType;
    }

    public List<Integer> getMapCfgIds() {
        return mapCfgIds;
    }

    public void setMapCfgIds(List<Integer> mapCfgIds) {
        this.mapCfgIds = mapCfgIds == null ? new ArrayList<>() : new ArrayList<>(mapCfgIds);
    }

    public List<Integer> getDutyIds() {
        return dutyIds;
    }

    public void setDutyIds(List<Integer> dutyIds) {
        this.dutyIds = dutyIds == null ? new ArrayList<>() : new ArrayList<>(dutyIds);
    }
}
