package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

import java.util.List;

/** OnlineService 的状态对账 RPC 门面。 */
@Actor
public final class OnlineStateReconcileActor {
    /** 校验 ConnService 上报的玩家连接快照。 */
    @Rpc
    public ConnStateCheck[] reconcileConnSessions(
            CallPoint connService, List<ConnStateCheck> entries) {
        return owner().stateReconcileManager().reconcileConnSessions(connService, entries);
    }

    /** 校验 PlayerService 上报的玩家运行态快照。 */
    @Rpc
    public PlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<PlayerStateCheck> entries) {
        return owner().stateReconcileManager().reconcilePlayerSessions(playerService, entries);
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
