package org.evd.game.StageService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

/** StageService 服务本体，只负责服务生命周期。 */
public class StageService extends Service {
    public StageService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }
}
