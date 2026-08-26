package org.evd.game.OnlineService.offline;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** OnlineService 离线 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlineOfflineRpc {
    @Rpc
    public void onSessionOffline(String userId, long playerId, CallPoint gate, long gateSessionId,
                                 int brokenTypeCode) {
        logic().onSessionOffline(userId, playerId, gate, gateSessionId, brokenTypeCode);
    }

    private OnlineOfflineLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineOfflineLogic.class);
    }
}
