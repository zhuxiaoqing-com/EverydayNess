package org.evd.game.OnlineService.disconnect;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallServiceInitDataSync;

import java.util.List;

/** OnlineService 连接恢复 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlineServiceConnectRpc {
    @Rpc
    public void restoreHistoricalPlayerServices(
            CallServiceInitDataSync syncData,
            List<String> userIds,
            CallPoint playerService) {
        if (!Service.checkSyncDataValid(syncData, "restoreHistoricalPlayerServices")) {
            return;
        }
        logic().restoreHistoricalPlayerServices(userIds, playerService);
    }

    private OnlineServiceConnectLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineServiceConnectLogic.class);
    }
}
