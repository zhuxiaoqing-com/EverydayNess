package org.evd.game.PlayerService.offline;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** PlayerService 玩家离线 RPC 入口。 */
@Actor
@RpcHandler
public final class PlayerOfflineRpc {
    @Rpc
    public void onPlayerOffline(String userId, long playerId, CallPoint gate,
                                long gateSessionId, int brokenTypeCode) {
        logic().onPlayerOffline(userId, playerId, gate, gateSessionId, brokenTypeCode);
    }

    private PlayerOfflineLogic logic() {
        return Service.getCurrent(PlayerService.class).getActor(PlayerOfflineLogic.class);
    }
}
