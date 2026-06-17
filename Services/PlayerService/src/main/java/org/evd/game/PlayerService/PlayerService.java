package org.evd.game.PlayerService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;

@Actor()
public class PlayerService extends Service {

    public PlayerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }
}
