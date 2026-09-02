package org.evd.game.SceneManagerService.scene;

import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.call.CallPoint;

import java.util.ArrayList;
import java.util.List;

/** SceneManager 记录的一个 SMapKey 对应场景。 */
public final class SMSceneInfo {
    private final SMapKey mapKey;
    private final long sceneId;
    private final List<SMapEnterRequest> waitEnterQueue = new ArrayList<>();
    private SceneState state;
    private final CallPoint stageCallPoint;

    public SMSceneInfo(SMapKey mapKey, long sceneId, CallPoint stageCallPoint) {
        this.mapKey = new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
        this.sceneId = sceneId;
        this.stageCallPoint = stageCallPoint == null ? null : new CallPoint(stageCallPoint);
        this.state = SceneState.CREATING;
    }

    public SMapKey getMapKey() {
        return new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
    }

    public long getSceneId() {
        return sceneId;
    }

    public List<SMapEnterRequest> getWaitEnterQueue() {
        return waitEnterQueue;
    }

    public SceneState getState() {
        return state;
    }

    public void setState(SceneState state) {
        this.state = state;
    }

    public CallPoint getStageCallPoint() {
        return stageCallPoint == null ? null : new CallPoint(stageCallPoint);
    }
}
