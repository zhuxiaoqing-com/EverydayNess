package org.evd.game.LobbyService;

import org.evd.game.LobbyService.session.LobbySessionRepository;
import org.evd.game.LobbyService.session.LobbyUserState;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.support.LogCore;

public final class LobbyOfflineActor {
    @Rpc
    public void onSessionOffline(String userId, long playerId, CallPoint gate, long sessionId, int brokenTypeCode) {
        LobbyService owner = owner();
        LobbySessionRepository sessionRepository = owner.sessionRepository();
        LobbyUserState userState = sessionRepository.findUser(userId);
        if (userState == null) {
            return;
        }
        if (userState.getActiveGate() == null
                || !userState.getActiveGate().equals(gate)
                || userState.getActiveSessionId() != sessionId) {
            return;
        }

        BrokenType brokenType = BrokenType.fromCode(brokenTypeCode);
        sessionRepository.clearActiveSession(userState);

        if (userState.getActivePlayerService() != null && playerId > 0L) {
            PlayerServiceProxy.inst().onPlayerOffline(
                    userState.getActivePlayerService(),
                    userId,
                    playerId,
                    brokenType.getCode()
            );
            userState.setActivePlayerService(null);
        }

        LogCore.core.info("LobbyService 处理离线: service={}, userId={}, playerId={}, brokenType={}",
                owner.getId(), userId, playerId, brokenType);
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }
}
