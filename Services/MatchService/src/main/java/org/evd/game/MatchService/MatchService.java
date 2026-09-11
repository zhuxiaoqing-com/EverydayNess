package org.evd.game.MatchService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

/** MatchService 服务本体，只负责匹配逻辑生命周期。 */
public final class MatchService extends Service {
    private static final long MATCH_INTERVAL_MILLIS = 1_000L;

    public MatchService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    public void init() {
        super.init();
        newRepeatedTimerCoroutine(MATCH_INTERVAL_MILLIS, true,
                () -> getActor(MatchMatchingLogic.class).match());
    }
}
