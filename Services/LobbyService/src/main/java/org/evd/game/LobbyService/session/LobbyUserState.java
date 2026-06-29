package org.evd.game.LobbyService.session;

import org.evd.game.LobbyService.LobbyRole;
import org.evd.game.runtime.call.CallPoint;

public final class LobbyUserState {
    private final String userId;
    private LobbyRole role;
    private String pendingToken;
    private CallPoint activeGate;
    private long activeSessionId;
    private CallPoint activePlayerService;

    public LobbyUserState(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public LobbyRole getRole() {
        return role;
    }

    public void setRole(LobbyRole role) {
        this.role = role;
    }

    public String getPendingToken() {
        return pendingToken;
    }

    public void setPendingToken(String pendingToken) {
        this.pendingToken = pendingToken;
    }

    public void clearPendingToken() {
        this.pendingToken = null;
    }

    public CallPoint getActiveGate() {
        return activeGate == null ? null : new CallPoint(activeGate);
    }

    public void setActiveGate(CallPoint activeGate) {
        this.activeGate = activeGate == null ? null : new CallPoint(activeGate);
    }

    public long getActiveSessionId() {
        return activeSessionId;
    }

    public void setActiveSessionId(long activeSessionId) {
        this.activeSessionId = activeSessionId;
    }

    public CallPoint getActivePlayerService() {
        return activePlayerService == null ? null : new CallPoint(activePlayerService);
    }

    public void setActivePlayerService(CallPoint activePlayerService) {
        this.activePlayerService = activePlayerService == null ? null : new CallPoint(activePlayerService);
    }
}
