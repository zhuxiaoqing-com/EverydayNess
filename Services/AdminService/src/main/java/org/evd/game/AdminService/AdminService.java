package org.evd.game.AdminService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;

public class AdminService extends Service {
    public AdminService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }
}
