package org.evd.game.LobbyService;

import org.evd.game.LobbyService.login.LobbyLoginActor;
import org.evd.game.LobbyService.routing.LobbyLoadBalancerActor;
import org.evd.game.LobbyService.session.LobbySessionRepository;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;

public class LobbyService extends Service {
    private final LobbySessionRepository sessionRepository;

    public LobbyService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.sessionRepository = new LobbySessionRepository();
    }

    public LobbySessionRepository sessionRepository() {
        return sessionRepository;
    }

    public LobbyLoadBalancerActor loadBalancerActor() {
        return getActor(LobbyLoadBalancerActor.class);
    }

    public LobbyLoginActor loginActor() {
        return getActor(LobbyLoginActor.class);
    }

    public LobbyRoleActor roleActor() {
        return getActor(LobbyRoleActor.class);
    }

    public LobbyOfflineActor offlineActor() {
        return getActor(LobbyOfflineActor.class);
    }
}
