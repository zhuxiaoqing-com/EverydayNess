package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.offline.OnlineOfflineCoordinator;
import org.evd.game.OnlineService.reconcile.gwonline.GwOnlineReconcileR;
import org.evd.game.OnlineService.reconcile.playeronline.PlayerOnlineReconcileR;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;
import org.evd.game.runtime.call.CallPoint;

import java.util.List;
import java.util.Map;

/** 三类对账器的薄门面；具体规则按对账方向分包维护。 */
public final class OnlineStateReconcileManager {
    private final GwOnlineReconcileR gwOnlineReconcileR;
    private final PlayerOnlineReconcileR playerOnlineReconcileR;

    public OnlineStateReconcileManager(OnlineSessionCoordinator sessionCoordinator,
                                       OnlineOfflineCoordinator offlineCoordinator) {
        this.gwOnlineReconcileR = new GwOnlineReconcileR(sessionCoordinator, offlineCoordinator);
        this.playerOnlineReconcileR = new PlayerOnlineReconcileR(sessionCoordinator, offlineCoordinator);
    }

    public List<ConnStateCheck> reconcileConnSessions(
            CallPoint connService, Map<String, ConnStateCheck> entries) {
        return gwOnlineReconcileR.reconcile(connService, entries);
    }

    public PlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<PlayerStateCheck> entries) {
        return playerOnlineReconcileR.reconcile(playerService, entries);
    }
}
