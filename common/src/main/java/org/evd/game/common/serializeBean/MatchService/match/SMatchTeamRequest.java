package org.evd.game.common.serializeBean.MatchService.match;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.ArrayList;
import java.util.List;

/** 组队匹配请求；队伍的副本候选必须是全队共享的候选集合。 */
@SerializeClass
public final class SMatchTeamRequest implements ISerializable {
    private long teamId;
    private int matchType;
    private List<Integer> mapCfgIds = new ArrayList<>();
    private List<SMatchPlayer> members = new ArrayList<>();

    public SMatchTeamRequest() {
    }

    public long getTeamId() {
        return teamId;
    }

    public void setTeamId(long teamId) {
        this.teamId = teamId;
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
        this.mapCfgIds = mapCfgIds == null ? new ArrayList<>() : mapCfgIds;
    }

    public List<SMatchPlayer> getMembers() {
        return members;
    }

    public void setMembers(List<SMatchPlayer> members) {
        this.members = members == null ? new ArrayList<>() : members;
    }
}
