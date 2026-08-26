package org.evd.game.common.serializeBean.OnlineService.reconcile;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/** PlayerService 上报的玩家绑定状态校验数据。 */
@SerializeClass
public class PlayerStateCheck implements ISerializable {
    private String userId;
    private long playerId;
    private CallPoint gate;
    private long gateSessionId;

    public PlayerStateCheck() {
    }

    public PlayerStateCheck(String userId, long playerId, CallPoint gate,
                            long gateSessionId) {
        this.userId = userId;
        this.playerId = playerId;
        this.gate = gate == null ? null : new CallPoint(gate);
        this.gateSessionId = gateSessionId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }
    public CallPoint getGate() { return gate == null ? null : new CallPoint(gate); }
    public void setGate(CallPoint gate) { this.gate = gate == null ? null : new CallPoint(gate); }
    public long getGateSessionId() { return gateSessionId; }
    public void setGateSessionId(long gateSessionId) { this.gateSessionId = gateSessionId; }
}
