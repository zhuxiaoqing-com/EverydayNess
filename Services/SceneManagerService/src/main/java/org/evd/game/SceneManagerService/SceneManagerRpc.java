package org.evd.game.SceneManagerService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.Service;

/** SceneManagerService 地图转场入口 RPC。 */
@Actor
@RpcHandler
public final class SceneManagerRpc {
    @Rpc
    public void enterMap(SMapEnterRequest request) {
        Service.getCurrent(SceneManagerService.class).getActor(SceneManagerLogic.class).enterMap(request);
    }

    @Rpc
    public CallPoint getSceneStage(long sceneId) {
        return Service.getCurrent(SceneManagerService.class).getSceneStage(sceneId);
    }
}
