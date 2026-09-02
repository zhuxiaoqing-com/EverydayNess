package org.evd.game.PlayerService.map;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;

/** Stage 回调 PlayerService 的地图转场 RPC 入口。 */
@Actor
@RpcHandler
public final class PlayerMapRpc {
    @Rpc
    public void readyEnterMap(long playerId, long transferId, SMapInfo targetInfo) {
        Service.getCurrent(PlayerService.class).getActor(PlayerMapLogic.class)
                .readyEnterMap(playerId, transferId, targetInfo);
    }
}
