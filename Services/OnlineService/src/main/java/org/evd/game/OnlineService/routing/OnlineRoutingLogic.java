package org.evd.game.OnlineService.routing;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlinePlayerCandidate;
import org.evd.game.runtime.Service;

/** OnlineService 负载选择业务逻辑。 */
@Actor
public final class OnlineRoutingLogic {
    /** 返回当前负载最低的 ConnService。 */
    public OnlineConnCandidate selectLeastLoadedConn() {
        return owner().serviceSelector().selectLeastLoadedConn();
    }

    /** 返回当前负载最低的 PlayerService。 */
    public OnlinePlayerCandidate selectLeastLoadedPlayer() {
        return owner().serviceSelector().selectLeastLoadedPlayer();
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
