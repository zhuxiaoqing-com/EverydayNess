package org.evd.game.StageService.mapCreate;

import org.evd.game.StageService.StageService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.runtime.Service;

/** StageService 地图创建和进入 RPC 入口。 */
@Actor
@RpcHandler
public final class StageServiceRpc {
    @Rpc
    public int getMapCount() {
        return logic().getMapCount();
    }

    @Rpc
    public boolean createScene(SMapKey mapKey, long sceneId) {
        return logic().createScene(mapKey, sceneId);
    }

    @Rpc
    public boolean prepareEnterScene(SMapEnterRequest request) {
        return logic().prepareEnterScene(request);
    }

    @Rpc
    public boolean enterScene(long sceneId, SPlayerMapData playerData) {
        return logic().enterScene(sceneId, playerData);
    }

    @Rpc
    public boolean exitScene(long sceneId, long playerId) {
        return logic().exitScene(sceneId, playerId);
    }

    @Rpc
    public boolean destroyScene(long sceneId) {
        return logic().destroyScene(sceneId);
    }

    private StageSceneLogic logic() {
        return Service.getCurrent(StageService.class).getActor(StageSceneLogic.class);
    }
}
