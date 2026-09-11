package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/** 玩家进入已创建地图的通用请求。 */
@SerializeClass
public final class PlayerEnterRequest implements ISerializable {
    private SPlayerMapSimpleData playerData;
    private long transferId;
    private CallPoint playerService;
    private SMapInfo oldMapInfo;
    private SMapInfo targetInfo;
    private SPlayerEnterParam enterParam;

    public PlayerEnterRequest() {
    }

    public PlayerEnterRequest(SPlayerMapSimpleData playerData, long transferId, CallPoint playerService,
                              SMapInfo oldMapInfo, SMapInfo targetInfo, SPlayerEnterParam enterParam) {
        this.playerData = playerData == null ? null : new SPlayerMapSimpleData(playerData);
        this.transferId = transferId;
        this.playerService = playerService == null ? null : new CallPoint(playerService);
        this.oldMapInfo = oldMapInfo == null ? null : new SMapInfo(oldMapInfo);
        this.targetInfo = targetInfo == null ? null : new SMapInfo(targetInfo);
        this.enterParam = enterParam == null ? null : new SPlayerEnterParam(enterParam);
    }

    public PlayerEnterRequest(PlayerEnterRequest other) {
        this(other == null ? null : other.playerData,
                other == null ? 0L : other.transferId,
                other == null ? null : other.playerService,
                other == null ? null : other.oldMapInfo,
                other == null ? null : other.targetInfo,
                other == null ? null : other.enterParam);
    }

    public long getPlayerId() {
        return playerData == null ? 0L : playerData.getPlayerId();
    }

    public SPlayerMapSimpleData getPlayerData() {
        return playerData;
    }

    public void setPlayerData(SPlayerMapSimpleData playerData) {
        this.playerData = playerData == null ? null : new SPlayerMapSimpleData(playerData);
    }

    public long getTransferId() {
        return transferId;
    }

    public void setTransferId(long transferId) {
        this.transferId = transferId;
    }

    public CallPoint getPlayerService() {
        return playerService;
    }

    public void setPlayerService(CallPoint playerService) {
        this.playerService = playerService == null ? null : new CallPoint(playerService);
    }

    public SMapInfo getOldMapInfo() {
        return oldMapInfo;
    }

    public void setOldMapInfo(SMapInfo oldMapInfo) {
        this.oldMapInfo = oldMapInfo == null ? null : new SMapInfo(oldMapInfo);
    }

    public SMapInfo getTargetInfo() {
        return targetInfo;
    }

    public void setTargetInfo(SMapInfo targetInfo) {
        this.targetInfo = targetInfo == null ? null : new SMapInfo(targetInfo);
    }

    public SPlayerEnterParam getEnterParam() {
        return enterParam;
    }

    public void setEnterParam(SPlayerEnterParam enterParam) {
        this.enterParam = enterParam == null ? null : new SPlayerEnterParam(enterParam);
    }
}
