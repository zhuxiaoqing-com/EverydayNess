package org.evd.game.LobbyService;

import org.evd.game.annotation.Actor;
import org.evd.game.LobbyService.login.LobbyLoginActor;
import org.evd.game.LobbyService.routing.LobbyLoadBalancerActor;
import org.evd.game.LobbyService.session.LobbySessionRepository;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;

@Actor()
public class LobbyService extends Service {
    private final LobbySessionRepository sessionRepository;
    private final LobbyLoadBalancerActor loadBalancerActor;
    private final LobbyLoginActor loginActor;
    private final LobbyRoleActor roleActor;
    private final LobbyOfflineActor offlineActor;

    public LobbyService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.sessionRepository = new LobbySessionRepository();
        this.loadBalancerActor = new LobbyLoadBalancerActor();
        this.loginActor = new LobbyLoginActor();
        this.roleActor = new LobbyRoleActor();
        this.offlineActor = new LobbyOfflineActor();
    }

    public LobbySessionRepository sessionRepository() {
        return sessionRepository;
    }

    public LobbyLoadBalancerActor loadBalancerActor() {
        return loadBalancerActor;
    }

    public LobbyLoginActor loginActor() {
        return loginActor;
    }

    public LobbyRoleActor roleActor() {
        return roleActor;
    }

    public LobbyOfflineActor offlineActor() {
        return offlineActor;
    }
}
