package org.evd.game.PlayerService.offline;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** PlayerService 的玩家离线业务逻辑。 */
@Actor
public final class PlayerOfflineLogic {
    /** 校验当前网关会话后注销玩家 Actor 和 Location。 */
    public void onPlayerOffline(String userId, long playerId, CallPoint gate,
                                long gateSessionId, int brokenTypeCode) {
        owner().offlineManager().onPlayerOffline(
                userId, playerId, gate, gateSessionId, brokenTypeCode);
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }
}
