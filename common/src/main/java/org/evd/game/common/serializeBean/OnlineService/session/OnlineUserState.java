package org.evd.game.common.serializeBean.OnlineService.session;

import org.evd.game.annotation.SerializeClass;
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
}
