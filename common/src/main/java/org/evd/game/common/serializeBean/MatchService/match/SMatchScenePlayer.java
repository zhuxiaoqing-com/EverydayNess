package org.evd.game.common.serializeBean.MatchService.match;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/** 多人匹配场景创建时的真实玩家数据。 */
@SerializeClass
public final class SMatchScenePlayer implements ISerializable {
    private long playerId;
    private CallPoint playerService;
    private SMatchEnterParams enterParams;

    public SMatchScenePlayer() {
    }

    public SMatchScenePlayer(long playerId, CallPoint playerService, SMatchEnterParams enterParams) {
        this.playerId = playerId;
        this.playerService = playerService == null ? null : new CallPoint(playerService);
        this.enterParams = enterParams == null ? null : new SMatchEnterParams(enterParams);
    }

    public SMatchScenePlayer(SMatchScenePlayer other) {
        this(other == null ? 0L : other.playerId,
                other == null ? null : other.playerService,
                other == null ? null : other.enterParams);
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public CallPoint getPlayerService() {
        return playerService;
    }

    public void setPlayerService(CallPoint playerService) {
        this.playerService = playerService == null ? null : new CallPoint(playerService);
    }

    public SMatchEnterParams getEnterParams() {
        return enterParams;
    }

    public void setEnterParams(SMatchEnterParams enterParams) {
        this.enterParams = enterParams == null ? null : new SMatchEnterParams(enterParams);
    }
}
