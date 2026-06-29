package org.evd.game.LobbyService.session;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;

import java.util.HashMap;
import java.util.Map;

public final class LobbySessionRepository {
    private final Map<String, LobbyUserState> userStates = new HashMap<>();
    private final Map<String, LobbyTokenState> tokenStates = new HashMap<>();

    public LobbyUserState getOrCreateUser(String userId) {
        return userStates.computeIfAbsent(userId, LobbyUserState::new);
    }

    public LobbyUserState findUser(String userId) {
        return userStates.get(userId);
    }

    public LobbyUserState findUser(ClientSessionRef session) {
        String userId = session.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userStates.get(userId);
    }

    public LobbyTokenState findToken(String token) {
        return tokenStates.get(token);
    }

    public void saveToken(String token, LobbyTokenState tokenState) {
        tokenStates.put(token, tokenState);
    }

    public void removeToken(String token) {
        tokenStates.remove(token);
    }

    public void invalidatePendingToken(LobbyUserState userState) {
        if (userState.getPendingToken() == null || userState.getPendingToken().isBlank()) {
            return;
        }
        tokenStates.remove(userState.getPendingToken());
        userState.clearPendingToken();
    }

    public void bindActiveSession(LobbyUserState userState, CallPoint gate, long sessionId) {
        userState.setActiveGate(gate == null ? null : new CallPoint(gate));
        userState.setActiveSessionId(sessionId);
    }

    public void clearActiveSession(LobbyUserState userState) {
        userState.setActiveGate(null);
        userState.setActiveSessionId(0L);
    }
}
