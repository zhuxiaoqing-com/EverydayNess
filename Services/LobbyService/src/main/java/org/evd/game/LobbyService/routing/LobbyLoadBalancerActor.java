package org.evd.game.LobbyService.routing;

import org.evd.game.LobbyService.LobbyService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.OnlineService.OnlineRoutingActorProxy;
import org.evd.game.common.serializeBean.OnlineService.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.OnlinePlayerCandidate;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

@Actor
public final class LobbyLoadBalancerActor {
    public LobbyConnCandidate selectLeastLoadedConn() {
        LobbyService owner = owner();
        CallPoint onlineRemote = owner.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        if (onlineRemote == null) {
            return null;
        }
        RpcResult<OnlineConnCandidate> result = OnlineRoutingActorProxy.callSelectLeastLoadedConn(onlineRemote);
        if (!result.isSuccess() || result.getValue() == null) {
            return null;
        }
        OnlineConnCandidate candidate = result.getValue();
        return new LobbyConnCandidate(candidate.getCallPoint(), candidate.getPublicAddr(), candidate.getLoginCount());
    }

    public LobbyPlayerCandidate selectLeastLoadedPlayer() {
        LobbyService owner = owner();
        CallPoint onlineRemote = owner.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        if (onlineRemote == null) {
            return null;
        }
        RpcResult<OnlinePlayerCandidate> result = OnlineRoutingActorProxy.callSelectLeastLoadedPlayer(onlineRemote);
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
