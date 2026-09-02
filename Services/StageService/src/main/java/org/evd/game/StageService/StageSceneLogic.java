package org.evd.game.StageService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.Service;

/** StageService 独立的 SceneBattle 业务逻辑。 */
@Actor
public final class StageSceneLogic {
    public int getMapCount() {
        return owner().getMapCount();
    }

    public boolean createScene(SMapKey mapKey, long sceneId) {
        return owner().createScene(mapKey, sceneId);
    }

    public void prepareEnterScene(SMapEnterRequest request) {
        owner().prepareEnterScene(request);
    }

    public boolean enterScene(long sceneId, long playerId, long transferId) {
        return owner().enterScene(sceneId, playerId, transferId);
    }

    public boolean exitScene(long sceneId, long playerId) {
        return owner().exitScene(sceneId, playerId);
    }

    public boolean destroyScene(long sceneId) {
        return owner().destroyScene(sceneId);
    }

    private StageService owner() {
        return Service.getCurrent(StageService.class);
    }
}
