package org.evd.game.OnlineService.offline;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** OnlineService 的离线和断线通知 RPC 入口。 */
@Actor
public final class OnlineOfflineActor {
    /** 接收 ConnService 的离线事件并交给离线协调器处理。 */
    @Rpc
    public void onSessionOffline(String userId, long playerId, CallPoint gate, long gateSessionId,
                                 int brokenTypeCode) {
        owner().offlineCoordinator().onSessionOffline(
                userId, playerId, gate, gateSessionId, brokenTypeCode);
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
