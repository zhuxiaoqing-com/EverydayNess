package org.evd.game.StageService.mapCreate;

import org.evd.game.StageService.StageService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;

/** StageService 地图创建和进入 RPC 入口。 */
@Actor
@RpcHandler
public final class StageServiceRpc {
    @Rpc
    public int getMapCount() {
        return logic().getMapCount();
    }

    @Rpc
    public SRunningMapInfo getRunningMapInfo(long sceneId) {
        return logic().getRunningMapInfo(sceneId);
    }

    @Rpc
    public boolean createScene(SMapKey mapKey, long sceneId) {
        return logic().createScene(mapKey, sceneId);
    }

    @Rpc
    public boolean createScene(SMapCreateRequest request, long sceneId) {
        return logic().createScene(request, sceneId);
    }

    @Rpc
    public boolean prepareEnterScene(PlayerEnterRequest request) {
        return logic().prepareEnterScene(request);
    }

    @Rpc
    public void enterScene(long sceneId, SPlayerMapData playerData) {
        logic().enterScene(sceneId, playerData);
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
