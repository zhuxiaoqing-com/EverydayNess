package org.evd.game.MatchService.disconnect;

import org.evd.game.MatchService.MatchLogic;
import org.evd.game.MatchService.MatchService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

/** MatchService 关联服务断开后的匹配池收敛逻辑。 */
@Actor
public final class MatchServiceDisconnectLogic {
    /** 移除断开服务关联的匹配队伍，并通知仍可达的关联服务。 */
    public void onServiceDisconnect(Collection<RegisteredService> services) {
        MatchLogic matchLogic = owner().getActor(MatchLogic.class);
        for (RegisteredService service : services) {
            matchLogic.onServiceDisconnect(service);
        }
    }

    private MatchService owner() {
        return Service.getCurrent(MatchService.class);
    }
}
