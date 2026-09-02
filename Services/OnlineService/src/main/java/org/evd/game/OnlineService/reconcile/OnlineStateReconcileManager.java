package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.offline.OnlineOfflineCoordinator;
import org.evd.game.OnlineService.reconcile.gwonline.GwOnlineReconcileR;
import org.evd.game.OnlineService.reconcile.playeronline.PlayerOnlineReconcileR;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SPlayerStateCheck;
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

    public List<SConnStateCheck> reconcileConnSessions(
            CallPoint connService, Map<String, SConnStateCheck> entries) {
        return gwOnlineReconcileR.reconcile(connService, entries);
    }

    public SPlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<SPlayerStateCheck> entries) {
        return playerOnlineReconcileR.reconcile(playerService, entries);
    }
}
