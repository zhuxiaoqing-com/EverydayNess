package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

import java.util.List;
import java.util.Map;

/** OnlineService 状态对账 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlineStateReconcileRpc {
    @Rpc
    public List<ConnStateCheck> reconcileConnSessions(
            CallPoint connService, Map<String, ConnStateCheck> entries) {
        return logic().reconcileConnSessions(connService, entries);
    }

    @Rpc
    public PlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<PlayerStateCheck> entries) {
        return logic().reconcilePlayerSessions(playerService, entries);
    }

    private OnlineStateReconcileLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineStateReconcileLogic.class);
    }
}
