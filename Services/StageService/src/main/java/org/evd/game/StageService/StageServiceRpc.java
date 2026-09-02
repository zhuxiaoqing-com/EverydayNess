package org.evd.game.StageService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.Service;

/** StageService SceneBattle 生命周期和玩家进出地图 RPC。 */
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
    public void prepareEnterScene(SMapEnterRequest request) {
        logic().prepareEnterScene(request);
    }

    @Rpc
    public boolean enterScene(long sceneId, long playerId, long transferId) {
        return logic().enterScene(sceneId, playerId, transferId);
    }

    @Rpc
    public boolean exitScene(long sceneId, long playerId) {
        return logic().exitScene(sceneId, playerId);
    }

    @Rpc
    public boolean destroyScene(long sceneId) {
        return logic().destroyScene(sceneId);
    }

    private StageService owner() {
        return Service.getCurrent(StageService.class);
    }

    private StageSceneLogic logic() {
        return owner().getActor(StageSceneLogic.class);
    }
}
