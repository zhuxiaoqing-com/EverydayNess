package org.evd.game.OnlineService.reconcile;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.offline.OnlineOfflineLogic;
import org.evd.game.OnlineService.reconcile.gwonline.GwOnlineReconcileR;
import org.evd.game.OnlineService.reconcile.playeronline.PlayerOnlineReconcileR;
import org.evd.game.OnlineService.session.OnlineSessionLogic;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SConnStateCheck;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SPlayerStateCheck;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

import java.util.List;
import java.util.Map;

/** OnlineService 的状态对账业务逻辑。 */
@Actor
public final class OnlineStateReconcileLogic {
    private final GwOnlineReconcileR gwReconcile = new GwOnlineReconcileR();
    private final PlayerOnlineReconcileR playerReconcile = new PlayerOnlineReconcileR();

    /** 校验 ConnService 上报的玩家连接快照。 */
    public List<SConnStateCheck> reconcileConnSessions(
            CallPoint connService, Map<String, SConnStateCheck> entries) {
        return gwReconcile.reconcile(connService, entries, session(), offline());
    }

    /** 校验 PlayerService 上报的玩家运行态快照。 */
    public SPlayerStateCheck[] reconcilePlayerSessions(
            CallPoint playerService, List<SPlayerStateCheck> entries) {
        return playerReconcile.reconcile(playerService, entries, session(), offline());
    }

    private OnlineSessionLogic session() {
        return owner().getActor(OnlineSessionLogic.class);
    }

    private OnlineOfflineLogic offline() {
        return owner().getActor(OnlineOfflineLogic.class);
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
