package org.evd.game.SceneManagerService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MapRoute;
import org.evd.game.runtime.Service;

/** SceneManagerService 地图实例目录 RPC。 */
@Actor
@RpcHandler
public final class SceneManagerRpc {
    @Rpc
    public MapRoute acquireMap(int mapConfigId) {
        return Service.getCurrent(SceneManagerService.class).acquireMap(mapConfigId);
    }
}
