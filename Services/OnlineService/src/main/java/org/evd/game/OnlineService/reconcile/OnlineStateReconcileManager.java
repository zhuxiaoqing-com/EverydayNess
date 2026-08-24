package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;
import org.evd.game.common.serializeBean.OnlineService.session.OnlineUserState;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;

/** OnlineService 的状态对账校验器，OnlineSessionCoordinator 仍是状态源。 */
public final class OnlineStateReconcileManager {
    private final OnlineSessionCoordinator sessionCoordinator;

    public OnlineStateReconcileManager(OnlineSessionCoordinator sessionCoordinator) {
        this.sessionCoordinator = sessionCoordinator;
    }

    /** 校验 ConnService 上报的玩家连接快照。 */
    public ConnStateCheck[] reconcileConnSessions(
            CallPoint connService, List<ConnStateCheck> entries) {
        List<ConnStateCheck> invalidEntries = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return new ConnStateCheck[0];
        }
        for (ConnStateCheck entry : entries) {
            if (entry == null || entry.getUserId() == null || entry.getUserId().isBlank()
                    || entry.getPlayerId() <= 0L || entry.getGateSessionId() <= 0L) {
                continue;
            }
            if (!isValidConnSession(connService, entry)) {
                invalidEntries.add(entry);
            }
        }
        if (!invalidEntries.isEmpty()) {
            LogCore.core.warn("OnlineService GW 对账发现不一致: source={}, invalidCount={}, totalCount={}",
                    connService, invalidEntries.size(), entries.size());
        }
        return invalidEntries.toArray(ConnStateCheck[]::new);
    }

    /** 校验 PlayerService 上报的玩家运行态快照。 */
    public PlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<PlayerStateCheck> entries) {
        List<PlayerStateCheck> invalidEntries = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return new PlayerStateCheck[0];
        }
        for (PlayerStateCheck entry : entries) {
            if (entry == null || entry.getUserId() == null || entry.getUserId().isBlank()
                    || entry.getPlayerId() <= 0L || entry.getGateSessionId() <= 0L) {
                continue;
            }
            if (!isValidPlayerSession(playerService, entry)) {
                invalidEntries.add(entry);
            }
        }
        if (!invalidEntries.isEmpty()) {
            LogCore.core.warn("OnlineService PlayerService 对账发现不一致: source={}, invalidCount={}, totalCount={}",
                    playerService, invalidEntries.size(), entries.size());
        }
        return invalidEntries.toArray(PlayerStateCheck[]::new);
    }

    private boolean isValidConnSession(CallPoint connService, ConnStateCheck entry) {
        OnlineUserState userState = sessionCoordinator.getUserState(entry.getUserId());
        return connService != null
                && matchesSession(userState, connService, entry.getGateSessionId())
                && userState.getActivePlayerId() == entry.getPlayerId()
                && userState.getActivePlayerService() != null
                && sessionCoordinator.getOnlinePlayer(entry.getUserId()) != null;
    }

    private boolean isValidPlayerSession(CallPoint playerService, PlayerStateCheck entry) {
        OnlineUserState userState = sessionCoordinator.getUserState(entry.getUserId());
        return playerService != null
                && matchesSession(userState, entry.getGate(), entry.getGateSessionId())
                && userState.getActivePlayerId() == entry.getPlayerId()
                && playerService.equals(userState.getActivePlayerService())
                && sessionCoordinator.getOnlinePlayer(entry.getUserId()) != null;
    }

    private boolean matchesSession(OnlineUserState userState, CallPoint gate,
                                   long gateSessionId) {
        return userState != null
                && gate != null
                && gate.equals(userState.getActiveGate())
                && gateSessionId == userState.getActiveGateSessionId();
    }
}
