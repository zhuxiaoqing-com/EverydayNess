package org.evd.game.OnlineService.routing;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.OnlineService.routing.SOnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.routing.SOnlinePlayerCandidate;
import org.evd.game.runtime.Service;

/** OnlineService 负载选择业务逻辑。 */
@Actor
public final class OnlineRoutingLogic {
    /** 返回当前负载最低的 ConnService。 */
    public SOnlineConnCandidate selectLeastLoadedConn() {
        return owner().serviceSelector().selectLeastLoadedConn();
    }

    /** 返回当前负载最低的 PlayerService。 */
    public SOnlinePlayerCandidate selectLeastLoadedPlayer() {
        return owner().serviceSelector().selectLeastLoadedPlayer();
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
