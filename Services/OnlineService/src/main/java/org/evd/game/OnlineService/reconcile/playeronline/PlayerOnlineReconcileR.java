package org.evd.game.OnlineService.reconcile.playeronline;

import org.evd.game.OnlineService.offline.OnlineOfflineLogic;
import org.evd.game.OnlineService.session.OnlinePlayer;
import org.evd.game.OnlineService.session.OnlineSessionLogic;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SPlayerStateCheck;
import org.evd.game.common.serializeBean.OnlineService.session.SOnlineUserState;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;

/** Player → Online 对账。 */
public final class PlayerOnlineReconcileR {
    public SPlayerStateCheck[] reconcile(CallPoint playerService, List<SPlayerStateCheck> entries,
                                         OnlineSessionLogic session,
                                         OnlineOfflineLogic offline) {
        if (playerService == null) {
            throw new IllegalArgumentException("OnlineService Player 对账 source CallPoint 不能为空");
        }
        List<SPlayerStateCheck> invalidEntries = new ArrayList<>();
        for (SPlayerStateCheck entry : entries) {
            SOnlineUserState onlineState = session.getUserState(entry.getUserId());
            if (onlineState == null) {
                invalidEntries.add(entry);
            } else if (isFullyOnline(onlineState, session)
                    && isPlayerStateMismatch(playerService, entry, onlineState)) {
                invalidEntries.add(entry);
            }
        }

        List<SOnlineUserState> statesToRepair = new ArrayList<>();
        for (SOnlineUserState onlineState : session.getUserStates()) {
            if (!isFullyOnline(onlineState, session)
                    || !playerService.equals(onlineState.getActivePlayerService())) {
                continue;
            }
            SPlayerStateCheck playerEntry = findEntry(entries, onlineState.getUserId());
            if (playerEntry == null || isPlayerStateMismatch(playerService, playerEntry, onlineState)) {
                if (onlineState.observePlayerReconcileMismatch()) {
                    statesToRepair.add(onlineState);
                }
            } else {
                onlineState.clearPlayerReconcileMismatch();
            }
        }

        for (SOnlineUserState onlineState : statesToRepair) {
            offline.kickGateway(
                    onlineState.getActiveGate(), onlineState.getActiveGateSessionId(),
                    BrokenType.STATE_RECONCILE, "player state reconcile mismatch");
            offline.offlineSession(onlineState.getUserId(),
                    onlineState.getActiveGate(), onlineState.getActiveGateSessionId(),
                    BrokenType.STATE_RECONCILE);
        }

        logMismatch(playerService, invalidEntries.size(), entries.size());
        return invalidEntries.toArray(SPlayerStateCheck[]::new);
    }

    private SPlayerStateCheck findEntry(List<SPlayerStateCheck> entries, String userId) {
        for (SPlayerStateCheck entry : entries) {
            if (userId.equals(entry.getUserId())) {
                return entry;
            }
        }
        return null;
    }

    private boolean isPlayerStateMismatch(CallPoint playerService, SPlayerStateCheck entry,
                                          SOnlineUserState onlineState) {
        return !playerService.equals(onlineState.getActivePlayerService())
                || entry.getPlayerId() != onlineState.getActivePlayerId()
                || entry.getGateSessionId() != onlineState.getActiveGateSessionId()
                || entry.getGate() == null
                || !entry.getGate().equals(onlineState.getActiveGate());
    }

    private boolean isFullyOnline(SOnlineUserState state, OnlineSessionLogic session) {
        if (state == null) {
            return false;
        }
        OnlinePlayer onlinePlayer = session.getOnlinePlayer(state.getUserId());
        return onlinePlayer != null && onlinePlayer.getStatus() == OnlinePlayer.Status.ONLINE;
    }

    private void logMismatch(CallPoint playerService, int invalidCount, int totalCount) {
        if (invalidCount > 0) {
            LogCore.core.warn("OnlineService Player-Online 对账发现不一致: source={}, invalidCount={}, totalCount={}",
                    playerService, invalidCount, totalCount);
        }
    }

}
