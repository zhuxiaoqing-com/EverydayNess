package org.evd.game.LobbyService;

import org.evd.game.annotation.Actor;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;

@Actor()
public class LobbyService extends Service {

    public LobbyService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }
}
