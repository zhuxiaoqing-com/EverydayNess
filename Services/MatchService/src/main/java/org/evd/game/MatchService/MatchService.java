package org.evd.game.MatchService;

import org.evd.game.MatchService.disconnect.MatchServiceDisconnectLogic;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

/** MatchService 服务本体，只负责匹配逻辑生命周期。 */
public final class MatchService extends Service {
    private static final long MATCH_INTERVAL_MILLIS = 1_000L;

    public MatchService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> services) {
        LogCore.core.info("MatchService 开始处理关联服务断开: service={}, count={}", getId(), services.size());
        getActor(MatchServiceDisconnectLogic.class).onServiceDisconnect(services);
        LogCore.core.info("MatchService 完成关联服务断开处理: service={}, count={}", getId(), services.size());
    }

    @Override
    public void init() {
        super.init();
        newRepeatedTimerCoroutine(MATCH_INTERVAL_MILLIS, true,
                () -> getActor(MatchMatchingLogic.class).match());
    }
}
