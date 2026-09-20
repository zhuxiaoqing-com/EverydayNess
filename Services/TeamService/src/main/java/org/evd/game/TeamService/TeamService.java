package org.evd.game.TeamService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.support.LogCore;
import java.util.Collection;

/** TeamService 组队匹配结果服务。 */
public final class TeamService extends Service {
    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> services) {
        LogCore.core.info("TeamService 开始处理关联服务断开: service={}, count={}", getId(), services.size());
        getActor(TeamLogic.class).onMatchServiceDisconnect(services);
        getActor(TeamLogic.class).onPlayerServiceDisconnect(services);
        LogCore.core.info("TeamService 完成 MatchService 断开处理: service={}, count={}", getId(), services.size());
    }

    public TeamService(Node node, String name, String scheduledName, int interval,
                       ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }
}
