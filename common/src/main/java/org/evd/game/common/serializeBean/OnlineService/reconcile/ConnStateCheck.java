package org.evd.game.common.serializeBean.OnlineService.reconcile;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;

/** ConnService 上报的玩家连接状态校验数据。 */
@SerializeClass
public class ConnStateCheck implements ISerializable {
    private String userId;
    private long playerId;
    private long gateSessionId;

    public ConnStateCheck() {
    }

    public ConnStateCheck(String userId, long playerId, long gateSessionId) {
        this.userId = userId;
        this.playerId = playerId;
        this.gateSessionId = gateSessionId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }
    public long getGateSessionId() { return gateSessionId; }
    public void setGateSessionId(long gateSessionId) { this.gateSessionId = gateSessionId; }
}
