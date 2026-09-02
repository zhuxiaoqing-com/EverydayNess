package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SPlayerStateCheck;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

import java.util.List;
import java.util.Map;

/** OnlineService 状态对账 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlineStateReconcileRpc {
    @Rpc
    public List<SConnStateCheck> reconcileConnSessions(
            CallPoint connService, Map<String, SConnStateCheck> entries) {
        return logic().reconcileConnSessions(connService, entries);
    }

    @Rpc
    public SPlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<SPlayerStateCheck> entries) {
        return logic().reconcilePlayerSessions(playerService, entries);
    }

    private OnlineStateReconcileLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineStateReconcileLogic.class);
    }
}
