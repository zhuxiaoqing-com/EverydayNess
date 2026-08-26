package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

import java.util.List;
import java.util.Map;

/** OnlineService 的状态对账业务逻辑。 */
@Actor
public final class OnlineStateReconcileLogic {
    /** 校验 ConnService 上报的玩家连接快照。 */
    public List<ConnStateCheck> reconcileConnSessions(
            CallPoint connService, Map<String, ConnStateCheck> entries) {
        return owner().stateReconcileManager().reconcileConnSessions(connService, entries);
    }

    /** 校验 PlayerService 上报的玩家运行态快照。 */
    public PlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<PlayerStateCheck> entries) {
        return owner().stateReconcileManager().reconcilePlayerSessions(playerService, entries);
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
