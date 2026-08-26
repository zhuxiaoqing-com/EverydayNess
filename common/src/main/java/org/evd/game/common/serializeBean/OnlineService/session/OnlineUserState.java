package org.evd.game.common.serializeBean.OnlineService.session;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.call.CallPoint;

@SerializeClass
public class OnlineUserState implements ISerializable {
    private String userId;
    private CallPoint activeGate;
    private long activeGateSessionId;
    private long activePlayerId;
    private CallPoint activePlayerService;
    private ActorAddress activePlayerActorAddress;
    private ActorAddress activeGateActorAddress;
    /** 对账异常计数属于当前正式在线对象；用户新 Session 会创建新的状态对象。 */
    private int gwMissingCount;
    private int playerMissingCount;

    public OnlineUserState() {
    }

    public OnlineUserState(String userId, CallPoint gate, long gateSessionId,
                           long playerId, CallPoint playerService,
                           ActorAddress playerActorAddress, ActorAddress gateActorAddress) {
        this.userId = userId;
        this.activeGate = gate == null ? null : new CallPoint(gate);
        this.activeGateSessionId = gateSessionId;
        this.activePlayerId = playerId;
        this.activePlayerService = playerService == null ? null : new CallPoint(playerService);
        this.activePlayerActorAddress = playerActorAddress == null
                ? null : new ActorAddress(playerActorAddress);
        this.activeGateActorAddress = gateActorAddress == null ? null : new ActorAddress(gateActorAddress);
    }

    public OnlineUserState(OnlineUserState other) {
        this.userId = other.userId;
        this.activeGate = other.activeGate == null ? null : new CallPoint(other.activeGate);
        this.activeGateSessionId = other.activeGateSessionId;
        this.activePlayerId = other.activePlayerId;
        this.activePlayerService = other.activePlayerService == null
                ? null : new CallPoint(other.activePlayerService);
        this.activePlayerActorAddress = other.activePlayerActorAddress == null
                ? null : new ActorAddress(other.activePlayerActorAddress);
        this.activeGateActorAddress = other.activeGateActorAddress == null
                ? null : new ActorAddress(other.activeGateActorAddress);
        this.gwMissingCount = other.gwMissingCount;
        this.playerMissingCount = other.playerMissingCount;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public CallPoint getActiveGate() {
        return activeGate;
    }

    public void setActiveGate(CallPoint activeGate) {
        this.activeGate = activeGate == null ? null : new CallPoint(activeGate);
    }

    public long getActiveGateSessionId() {
        return activeGateSessionId;
    }

    public void setActiveGateSessionId(long activeGateSessionId) {
        this.activeGateSessionId = activeGateSessionId;
    }

    public long getActivePlayerId() {
        return activePlayerId;
    }

    public void setActivePlayerId(long activePlayerId) {
        this.activePlayerId = activePlayerId;
    }

    public CallPoint getActivePlayerService() {
        return activePlayerService;
    }

    public void setActivePlayerService(CallPoint activePlayerService) {
        this.activePlayerService = activePlayerService == null ? null : new CallPoint(activePlayerService);
    }

    public ActorAddress getActivePlayerActorAddress() {
        return activePlayerActorAddress;
    }

    public void setActivePlayerActorAddress(ActorAddress activePlayerActorAddress) {
        this.activePlayerActorAddress = activePlayerActorAddress == null ? null : new ActorAddress(activePlayerActorAddress);
    }

    public ActorAddress getActiveGateActorAddress() {
        return activeGateActorAddress;
    }

    public void setActiveGateActorAddress(ActorAddress activeGateActorAddress) {
        this.activeGateActorAddress = activeGateActorAddress == null ? null : new ActorAddress(activeGateActorAddress);
    }

    public int getGwMissingCount() { return gwMissingCount; }
    public void setGwMissingCount(int gwMissingCount) { this.gwMissingCount = gwMissingCount; }
    public int getPlayerMissingCount() { return playerMissingCount; }
    public void setPlayerMissingCount(int playerMissingCount) { this.playerMissingCount = playerMissingCount; }

    /** 记录当前用户与 GW 的连续对账异常；返回值表示已连续发现两轮。 */
    public boolean observeGwReconcileMismatch() {
        return ++gwMissingCount >= 2;
    }

    /** 记录当前用户与 PlayerService 的连续对账异常；返回值表示已连续发现两轮。 */
    public boolean observePlayerReconcileMismatch() {
        return ++playerMissingCount >= 2;
    }

    public void clearGwReconcileMismatch() {
        gwMissingCount = 0;
    }

    public void clearPlayerReconcileMismatch() {
        playerMissingCount = 0;
    }
}
