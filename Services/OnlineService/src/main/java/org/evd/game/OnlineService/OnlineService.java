package org.evd.game.OnlineService;

import org.evd.game.annotation.Actor;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;

@Actor()
public class OnlineService extends Service {

    public OnlineService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    protected boolean supportMdb() {
        return false;
    }
}
