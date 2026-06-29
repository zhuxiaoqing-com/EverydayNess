package org.evd.game.LobbyService.session;

import org.evd.game.runtime.call.CallPoint;

public final class LobbyTokenState {
    private final String userId;
    private final CallPoint gateCallPoint;
    private final long expireAt;

    public LobbyTokenState(String userId, CallPoint gateCallPoint, long expireAt) {
        this.userId = userId;
        this.gateCallPoint = gateCallPoint == null ? null : new CallPoint(gateCallPoint);
        this.expireAt = expireAt;
    }

    public String getUserId() {
        return userId;
    }

    public CallPoint getGateCallPoint() {
        return gateCallPoint == null ? null : new CallPoint(gateCallPoint);
    }

    public long getExpireAt() {
        return expireAt;
    }
}
