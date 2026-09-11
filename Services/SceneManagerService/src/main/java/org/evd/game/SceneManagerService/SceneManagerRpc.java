package org.evd.game.SceneManagerService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.Service;

import java.util.List;

/** SceneManagerService 地图转场入口 RPC。 */
@Actor
@RpcHandler
public final class SceneManagerRpc {
    @Rpc
    public boolean enterMap(PlayerEnterRequest request) {
        return Service.getCurrent(SceneManagerService.class).getActor(SceneManagerLogic.class).enterMap(request);
    }

    @Rpc
    public SMapInfo createScene(SMapCreateRequest request) {
        return Service.getCurrent(SceneManagerService.class).getActor(SceneManagerLogic.class)
                .createScene(request);
    }

    @Rpc
    public boolean exitMap(SMapInfo mapInfo, long playerId) {
        return Service.getCurrent(SceneManagerService.class).getActor(SceneManagerLogic.class).exitMap(mapInfo, playerId);
    }

    @Rpc
    public CallPoint getSceneStage(long sceneId) {
        return Service.getCurrent(SceneManagerService.class).getSceneStage(sceneId);
    }

    @Rpc
    public List<SRunningMapInfo> getRunningMaps(int mapCfgId) {
        return Service.getCurrent(SceneManagerService.class).getActor(SceneManagerLogic.class)
                .getRunningMaps(mapCfgId);
    }
}
