package org.evd.game.SceneManagerService.scene;

import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.call.CallPoint;

import java.util.HashMap;
import java.util.Map;

/** SceneManager 记录的一个 SMapKey 对应场景。 */
public final class SMSceneInfo {
    private final SMapKey mapKey;
    private final long sceneId;
    private final Map<Long, PlayerEnterRequest> waitEnterQueue = new HashMap<>();
    private SMSceneState state;
    private final CallPoint stageCallPoint;

    public SMSceneInfo(SMapKey mapKey, long sceneId, CallPoint stageCallPoint) {
        this.mapKey = new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
        this.sceneId = sceneId;
        this.stageCallPoint = stageCallPoint == null ? null : new CallPoint(stageCallPoint);
        this.state = SMSceneState.CREATING;
    }

    public SMapKey getMapKey() {
        return new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
    }

    public long getSceneId() {
        return sceneId;
    }

    public Map<Long, PlayerEnterRequest> getWaitEnterQueue() {
        return waitEnterQueue;
    }

    public SMSceneState getState() {
        return state;
    }

    public void setState(SMSceneState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "SMSceneInfo{" +
                "mapCfgId=" + mapKey.getMapCfgId() +
                ", groupId=" + mapKey.getGroupId() +
                ", sceneId=" + sceneId +
                ", state=" + state +
                ", waitEnterCount=" + waitEnterQueue.size() +
                ", stageCallPoint=" + stageCallPoint +
                '}';
    }

    public CallPoint getStageCallPoint() {
        return stageCallPoint == null ? null : new CallPoint(stageCallPoint);
    }
}
