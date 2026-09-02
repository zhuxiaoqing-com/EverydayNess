package org.evd.game.OnlineService.reconcile.gwonline;

import org.evd.game.OnlineService.offline.OnlineOfflineCoordinator;
import org.evd.game.OnlineService.session.OnlinePlayer;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.session.SOnlineUserState;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GW → Online 对账。 */
public final class GwOnlineReconcileR {
    private final OnlineSessionCoordinator sessionCoordinator;
    private final OnlineOfflineCoordinator offlineCoordinator;

    public GwOnlineReconcileR(OnlineSessionCoordinator sessionCoordinator,
                              OnlineOfflineCoordinator offlineCoordinator) {
        this.sessionCoordinator = sessionCoordinator;
        this.offlineCoordinator = offlineCoordinator;
    }

    public List<SConnStateCheck> reconcile(CallPoint connService,
                                          Map<String, SConnStateCheck> reportedEntries) {
        if (connService == null) {
            throw new IllegalArgumentException("OnlineService GW 对账 source CallPoint 不能为空");
        }
        List<SConnStateCheck> invalidEntries = new ArrayList<>();
        for (SConnStateCheck entry : reportedEntries.values()) {
            SOnlineUserState onlineState = sessionCoordinator.getUserState(entry.getUserId());
            if (onlineState == null) {
                invalidEntries.add(entry);
                continue;
            }
            if (isFullyOnline(onlineState)
                    && !sameConnState(connService, entry, onlineState)) {
                invalidEntries.add(entry);
            }
        }

        List<SOnlineUserState> statesToOffline = new ArrayList<>();
        for (SOnlineUserState onlineState : sessionCoordinator.getUserStates()) {
            if (!isFullyOnline(onlineState)
                    || !connService.equals(onlineState.getActiveGate())) {
                continue;
            }
            SConnStateCheck connEntry = reportedEntries.get(onlineState.getUserId());
            if (connEntry == null || !sameConnState(connService, connEntry, onlineState)) {
                if (onlineState.observeGwReconcileMismatch()) {
                    statesToOffline.add(onlineState);
                }
            } else {
                onlineState.clearGwReconcileMismatch();
            }
        }

        for (SOnlineUserState onlineState : statesToOffline) {
            offlineCoordinator.offlineSession(onlineState.getUserId(),
                    onlineState.getActiveGate(), onlineState.getActiveGateSessionId(),
                    BrokenType.STATE_RECONCILE);
        }

        logMismatch(connService, invalidEntries.size(), reportedEntries.size());
        return invalidEntries;
    }

    private boolean sameConnState(CallPoint connService, SConnStateCheck entry,
                                   SOnlineUserState onlineState) {
        return connService.equals(onlineState.getActiveGate())
                && entry.getGateSessionId() == onlineState.getActiveGateSessionId()
                && entry.getPlayerId() == onlineState.getActivePlayerId();
    }

    private boolean isFullyOnline(SOnlineUserState state) {
        if (state == null) {
            return false;
        }
        OnlinePlayer onlinePlayer = sessionCoordinator.getOnlinePlayer(state.getUserId());
        return onlinePlayer != null && onlinePlayer.getStatus() == OnlinePlayer.Status.ONLINE;
    }

    private void logMismatch(CallPoint connService, int invalidCount, int totalCount) {
        if (invalidCount > 0) {
            LogCore.core.warn("OnlineService GW-Online 对账发现不一致: source={}, invalidCount={}, totalCount={}",
                    connService, invalidCount, totalCount);
        }
    }
}
