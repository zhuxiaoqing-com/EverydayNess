package org.evd.game.LobbyService.routing;

import org.evd.game.LobbyService.LobbyService;
import org.evd.game.annotation.Actor;
import org.evd.game.common.proxy.OnlineService.OnlineRoutingRpcProxy;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlinePlayerCandidate;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

@Actor
public final class LobbyLoadBalancerLogic {
    public LobbyConnCandidate selectLeastLoadedConn() {
        RpcResult<OnlineConnCandidate> result = OnlineRoutingRpcProxy.callSelectLeastLoadedConn(null);
        if (!result.isSuccess() || result.getValue() == null) {
            return null;
        }
        OnlineConnCandidate candidate = result.getValue();
        return new LobbyConnCandidate(candidate.getCallPoint(), candidate.getPublicAddr(), candidate.getLoginCount());
    }

    public LobbyPlayerCandidate selectLeastLoadedPlayer() {
        RpcResult<OnlinePlayerCandidate> result = OnlineRoutingRpcProxy.callSelectLeastLoadedPlayer(null);
        if (!result.isSuccess() || result.getValue() == null) {
            return null;
        }
        OnlinePlayerCandidate candidate = result.getValue();
        return new LobbyPlayerCandidate(candidate.getCallPoint(), candidate.getOnlineCount());
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }
}
