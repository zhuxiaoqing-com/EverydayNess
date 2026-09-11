package org.evd.game.common.serializeBean.MatchService.match;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

import java.util.ArrayList;
import java.util.List;

/** 单人匹配请求；单人进入 MatchService 后会被包装为一人的队伍。 */
@SerializeClass
public final class SMatchRequest implements ISerializable {
    private long playerId;
    private int matchType;
    /** 兼容职责匹配策略的玩家职责候选。 */
    private List<Integer> dutyIds = new ArrayList<>();
    private List<Integer> mapCfgIds = new ArrayList<>();
    private CallPoint playerService;

    public SMatchRequest() {
    }

    public SMatchRequest(long playerId, int matchType, List<Integer> mapCfgIds, CallPoint playerService) {
        this.playerId = playerId;
        this.matchType = matchType;
        if (mapCfgIds != null) {
            this.mapCfgIds.addAll(mapCfgIds);
        }
        this.playerService = playerService == null ? null : new CallPoint(playerService);
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getMatchType() {
        return matchType;
    }

    public void setMatchType(int matchType) {
        this.matchType = matchType;
    }

    public List<Integer> getDutyIds() {
        return dutyIds;
    }

    public void setDutyIds(List<Integer> dutyIds) {
        this.dutyIds = dutyIds == null ? new ArrayList<>() : dutyIds;
    }

    public List<Integer> getMapCfgIds() {
        return mapCfgIds;
    }

    public void setMapCfgIds(List<Integer> mapCfgIds) {
        this.mapCfgIds = mapCfgIds == null ? new ArrayList<>() : mapCfgIds;
    }

    public CallPoint getPlayerService() {
        return playerService == null ? null : new CallPoint(playerService);
    }

    public void setPlayerService(CallPoint playerService) {
        this.playerService = playerService == null ? null : new CallPoint(playerService);
    }
}
