package org.evd.game.LobbyService.routing;

import org.evd.game.LobbyService.LobbyService;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.RegisteredService;

import java.util.List;

public final class LobbyLoadBalancerActor {
    public LobbyConnCandidate selectLeastLoadedConn() {
        LobbyService owner = owner();
        List<RegisteredService> services = owner.getNode().getServicesByType(ServiceType.CONN);
        LobbyConnCandidate best = null;
        for (RegisteredService service : services) {
            CallPoint callPoint = service.getCallPoint();
            String publicAddr = ConnServiceProxy.inst().getPublicAddr(callPoint);
            if (publicAddr == null || publicAddr.isBlank()) {
                continue;
            }
            int loginCount = ConnServiceProxy.inst().getLoginSessionCount(callPoint);
            if (best == null || loginCount < best.loginCount()) {
                best = new LobbyConnCandidate(new CallPoint(callPoint), publicAddr, loginCount);
            }
        }
        return best;
    }

    public LobbyPlayerCandidate selectLeastLoadedPlayer() {
        LobbyService owner = owner();
        List<RegisteredService> services = owner.getNode().getServicesByType(ServiceType.PLAYER);
        LobbyPlayerCandidate best = null;
        for (RegisteredService service : services) {
            CallPoint callPoint = service.getCallPoint();
            int onlineCount = PlayerServiceProxy.inst().getOnlineCount(callPoint);
            if (best == null || onlineCount < best.onlineCount()) {
                best = new LobbyPlayerCandidate(new CallPoint(callPoint), onlineCount);
            }
        }
        return best;
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }
}
