package org.evd.game.PlayerService.map;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;

/** Stage 回调 PlayerService 的地图转场 RPC 入口。 */
@Actor
@RpcHandler
public final class PlayerMapRpc {
    @Rpc
    public boolean readyEnterMap(long playerId, long transferId, SMapInfo targetInfo) {
        return Service.getCurrent(PlayerService.class).getActor(PlayerMapLogic.class)
                .readyEnterMap(playerId, transferId, targetInfo);
    }

    @Rpc
    public boolean onEnterMap(long playerId, long transferId, SMapInfo targetInfo,
                              ActorAddress stageActorAddress) {
        return Service.getCurrent(PlayerService.class).getActor(PlayerMapLogic.class)
                .onEnterMap(playerId, transferId, targetInfo, stageActorAddress);
    }

    @Rpc
    public void onExitMap(long playerId, long sceneId, ActorAddress stageActorAddress) {
        Service.getCurrent(PlayerService.class).getActor(PlayerMapLogic.class)
                .onExitMap(playerId, sceneId, stageActorAddress);
    }
}
