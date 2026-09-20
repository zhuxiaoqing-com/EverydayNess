package org.evd.game.OnlineService.disconnect;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.session.OnlineSessionLogic;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

/** OnlineService 关联服务连接后的历史 PlayerService 绑定恢复逻辑。 */
@Actor
public final class OnlineServiceConnectLogic {
    /** 接收 PlayerService 当前 MDB 中仍保留的玩家，转发给会话逻辑覆盖历史绑定。 */
    public void restoreHistoricalPlayerServices(Collection<String> userIds, CallPoint playerService) {
        session().restoreHistoricalPlayerServices(userIds, playerService);
    }

    private OnlineSessionLogic session() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineSessionLogic.class);
    }

}
