package org.evd.game.PlayerService.offline;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** PlayerService 的玩家离线 RPC 入口。 */
@Actor
public final class PlayerOfflineActor {
    /** 校验当前网关会话后注销玩家 Actor 和 Location。 */
    @Rpc
    public void onPlayerOffline(String userId, long playerId, CallPoint gate,
                                long gateSessionId, int brokenTypeCode) {
        owner().offlineManager().onPlayerOffline(
                userId, playerId, gate, gateSessionId, brokenTypeCode);
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }
}
