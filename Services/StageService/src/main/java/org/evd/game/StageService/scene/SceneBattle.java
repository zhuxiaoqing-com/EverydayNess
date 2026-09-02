package org.evd.game.StageService.scene;

import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;

import java.util.HashMap;
import java.util.Map;

/** Stage 上所有具体场景的父类，负责预进入队列和正式玩家集合。 */
public class SceneBattle {
    private final SMapKey mapKey;
    private final long sceneId;
    private final Map<Long, SMapEnterRequest> pendingRoleMap = new HashMap<>();
    private final Map<Long, SMapEnterRequest> roleMap = new HashMap<>();

    public SceneBattle(SMapKey mapKey, long sceneId) {
        this.mapKey = new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
        this.sceneId = sceneId;
    }

    public boolean canEnter(SMapEnterRequest request) {
        if (request == null || request.getPlayerId() <= 0L || request.getTargetInfo() == null) {
            return false;
        }
        return sceneId == request.getTargetInfo().getSceneId()
                && mapKey.equals(request.getTargetInfo().toMapKey())
                && !roleMap.containsKey(request.getPlayerId())
                && !pendingRoleMap.containsKey(request.getPlayerId());
    }

    public void addPendingRole(SMapEnterRequest request) {
        pendingRoleMap.put(request.getPlayerId(), new SMapEnterRequest(request));
    }

    public boolean enter(long playerId, long transferId) {
        SMapEnterRequest request = pendingRoleMap.get(playerId);
        if (request == null || request.getTransferId() != transferId) {
            return false;
        }
        pendingRoleMap.remove(playerId);
        roleMap.put(playerId, request);
        return true;
    }

    public void cancelPending(long playerId) {
        pendingRoleMap.remove(playerId);
    }

    public boolean exit(long playerId) {
        pendingRoleMap.remove(playerId);
        roleMap.remove(playerId);
        return true;
    }

    public boolean isEmpty() {
        return pendingRoleMap.isEmpty() && roleMap.isEmpty();
    }

    public SMapKey getMapKey() {
        return new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
    }
}
