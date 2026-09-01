package org.evd.game.StageService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.Service;

/** StageService 地图实例和玩家进出地图 RPC。 */
@Actor
@RpcHandler
public final class StageServiceRpc {
    @Rpc
    public boolean createMap(long mapInstanceId, int mapConfigId) {
        return owner().createMap(mapInstanceId, mapConfigId);
    }

    @Rpc
    public boolean destroyMap(long mapInstanceId) {
        return owner().destroyMap(mapInstanceId);
    }

    @Rpc
    public boolean enterMap(long mapInstanceId, long playerId, long enterSeq) {
        return owner().enterMap(mapInstanceId, playerId, enterSeq);
    }

    @Rpc
    public boolean leaveMap(long mapInstanceId, long playerId, long enterSeq) {
        return owner().leaveMap(mapInstanceId, playerId, enterSeq);
    }

    private StageService owner() {
        return Service.getCurrent(StageService.class);
    }
}
