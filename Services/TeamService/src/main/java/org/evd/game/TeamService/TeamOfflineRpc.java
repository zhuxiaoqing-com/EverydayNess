package org.evd.game.TeamService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** TeamService 玩家离线状态收敛 RPC 入口。 */
@Actor
@RpcHandler
public final class TeamOfflineRpc {
    @Rpc
    public void onPlayerOffline(long playerId, CallPoint playerService) {
        Service.getCurrent(TeamService.class).getActor(TeamLogic.class)
                .onPlayerOffline(playerId, playerService);
    }
}
