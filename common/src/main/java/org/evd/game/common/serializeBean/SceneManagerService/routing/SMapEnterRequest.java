package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/** SceneManager 发往 Stage 的一次玩家预进入请求。 */
@SerializeClass
public final class SMapEnterRequest implements ISerializable {
    private long playerId;
    private long transferId;
    private CallPoint playerService;
    private SMapInfo oldMapInfo;
    private SMapInfo targetInfo;

    public SMapEnterRequest() {
    }

    public SMapEnterRequest(long playerId, long transferId, CallPoint playerService,
                           SMapInfo oldMapInfo, SMapInfo targetInfo) {
        this.playerId = playerId;
        this.transferId = transferId;
        this.playerService = playerService == null ? null : new CallPoint(playerService);
        this.oldMapInfo = oldMapInfo == null ? null : new SMapInfo(oldMapInfo);
        this.targetInfo = targetInfo == null ? null : new SMapInfo(targetInfo);
    }

    public SMapEnterRequest(SMapEnterRequest other) {
        this(other == null ? 0L : other.playerId,
                other == null ? 0L : other.transferId,
                other == null ? null : other.playerService,
                other == null ? null : other.oldMapInfo,
                other == null ? null : other.targetInfo);
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long getTransferId() {
        return transferId;
    }

    public void setTransferId(long transferId) {
        this.transferId = transferId;
    }

    public CallPoint getPlayerService() {
        return playerService == null ? null : new CallPoint(playerService);
    }

    public void setPlayerService(CallPoint playerService) {
        this.playerService = playerService == null ? null : new CallPoint(playerService);
    }

    public SMapInfo getOldMapInfo() {
        return oldMapInfo == null ? null : new SMapInfo(oldMapInfo);
    }

    public void setOldMapInfo(SMapInfo oldMapInfo) {
        this.oldMapInfo = oldMapInfo == null ? null : new SMapInfo(oldMapInfo);
    }

    public SMapInfo getTargetInfo() {
        return targetInfo == null ? null : new SMapInfo(targetInfo);
    }

    public void setTargetInfo(SMapInfo targetInfo) {
        this.targetInfo = targetInfo == null ? null : new SMapInfo(targetInfo);
    }
}
