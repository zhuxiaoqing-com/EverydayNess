package org.evd.game.OnlineService.routing;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.serializeBean.OnlineService.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.OnlinePlayerCandidate;
import org.evd.game.runtime.Service;

/** OnlineService 负载选择相关的 RPC 入口。 */
@Actor
public final class OnlineRoutingActor {
    /** 返回当前负载最低的 ConnService。 */
    @Rpc
    public OnlineConnCandidate selectLeastLoadedConn() {
        return owner().serviceSelector().selectLeastLoadedConn();
    }

    /** 返回当前负载最低的 PlayerService。 */
    @Rpc
    public OnlinePlayerCandidate selectLeastLoadedPlayer() {
        return owner().serviceSelector().selectLeastLoadedPlayer();
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
