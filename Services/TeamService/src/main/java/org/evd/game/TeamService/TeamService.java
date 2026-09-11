package org.evd.game.TeamService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

/** TeamService 组队匹配结果服务。 */
public final class TeamService extends Service {
    public TeamService(Node node, String name, String scheduledName, int interval,
                       ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }
}
