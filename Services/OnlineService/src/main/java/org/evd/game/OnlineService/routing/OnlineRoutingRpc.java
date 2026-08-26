package org.evd.game.OnlineService.routing;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlinePlayerCandidate;
import org.evd.game.runtime.Service;

/** OnlineService 负载选择 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlineRoutingRpc {
    @Rpc
    public OnlineConnCandidate selectLeastLoadedConn() {
        return logic().selectLeastLoadedConn();
    }

    @Rpc
    public OnlinePlayerCandidate selectLeastLoadedPlayer() {
        return logic().selectLeastLoadedPlayer();
    }

    private OnlineRoutingLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineRoutingLogic.class);
    }
}
