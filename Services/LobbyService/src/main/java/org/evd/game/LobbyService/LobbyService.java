package org.evd.game.LobbyService;

import org.evd.game.LobbyService.routing.LobbyLoadBalancerActor;
import org.evd.game.LobbyService.session.LobbySessionRepository;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.TickTimer;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.continuation.ContinuationLockScope;

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


    @Override
    protected void init_t() {
        super.init_t();

    }

    TickTimer tickTimer = new TickTimer(5000);
    @Override
    public void tick() {
        super.tick();
        if (!tickTimer.isPeriod(getTimeCurrent())) {
            return;
        }

     /*   launchCoroutine(() -> {
            int onlineCount = PlayerServiceProxy.inst().getOnlineCount(node.getAnyCallPointByType(ServiceType.PLAYER));
        });

        launchCoroutine(() -> {
            System.out.println("---");
        });
        launchCoroutine(() -> {
            ContinuationLockScope continuationLockScope = awaitCoroutineLockScope(1, new Object());
            ContinuationLockScope a = awaitCoroutineLockScope(1, new Object());
        });

        launchCoroutine(() -> {
                logCoroutineDebugDump("shutdown timeout");
        });*/
    }
}
