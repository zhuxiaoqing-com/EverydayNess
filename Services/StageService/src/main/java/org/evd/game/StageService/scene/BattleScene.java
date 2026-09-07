package org.evd.game.StageService.scene;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.common.proxy.PlayerService.PlayerMapRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.HashMap;
import java.util.Map;

/** Stage 上所有具体场景的父类，负责预进入队列和正式玩家集合。 */
@Slf4j
public class BattleScene {
    private final SMapKey mapKey;
    private final long sceneId;
    private final Map<Long, SMapEnterRequest> pendingRoleMap = new HashMap<>();
    private final Map<Long, SPlayerMapData> roleMap = new HashMap<>();
    private final Map<Long, CallPoint> playerServiceMap = new HashMap<>();

    public BattleScene(SMapKey mapKey, long sceneId) {
        this.mapKey = new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
        this.sceneId = sceneId;
    }

    public boolean canEnter(SMapEnterRequest request) {
        if (request == null) {
            log.warn("BattleScene 拒绝进入：请求为空, sceneId={}", sceneId);
            return false;
        }
        long playerId = request.getPlayerId();
        if (playerId <= 0L) {
            log.warn("BattleScene 拒绝进入：playerId 无效, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        SMapInfo targetInfo = request.getTargetInfo();
        if (targetInfo == null) {
            log.warn("BattleScene 拒绝进入：目标地图信息为空, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        if (sceneId != targetInfo.getSceneId()) {
            log.warn("BattleScene 拒绝进入：sceneId 不匹配, playerId={}, sceneId={}, targetSceneId={}",
                    playerId, sceneId, targetInfo.getSceneId());
            return false;
        }
        if (!mapKey.equals(targetInfo.toMapKey())) {
            log.warn("BattleScene 拒绝进入：地图标识不匹配, playerId={}, sceneId={}, mapCfgId={}, groupId={}, targetMapCfgId={}, targetGroupId={}",
                    playerId, sceneId, mapKey.getMapCfgId(), mapKey.getGroupId(),
                    targetInfo.getMapCfgId(), targetInfo.getGroupId());
            return false;
        }
        if (roleMap.containsKey(playerId)) {
            log.info("BattleScene 拒绝进入：玩家已在场景中, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        if (pendingRoleMap.containsKey(playerId)) {
            log.info("BattleScene 拒绝进入：玩家已在预进入队列中, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        return true;
    }

    public void addPendingRole(SMapEnterRequest request) {
        pendingRoleMap.put(request.getPlayerId(), request);
    }

    public boolean enter(SPlayerMapData playerData) {
        if (playerData == null || playerData.getPlayerId() <= 0L) {
            return false;
        }
        long playerId = playerData.getPlayerId();
        SMapEnterRequest request = pendingRoleMap.remove(playerId);
        if (request != null) {
            roleMap.put(playerId, new SPlayerMapData(playerData));
            playerServiceMap.put(playerId, request.getPlayerService());
            return true;
        }
        if (!roleMap.containsKey(playerId)) {
            return false;
        }
        roleMap.put(playerId, new SPlayerMapData(playerData));
        return true;
    }

    public void cancelPending(long playerId) {
        pendingRoleMap.remove(playerId);
    }

    public boolean exit(long playerId) {
        CallPoint playerService = playerServiceMap.remove(playerId);
        if (playerService == null) {
            SMapEnterRequest request = pendingRoleMap.get(playerId);
            playerService = request == null ? null : request.getPlayerService();
        }
        pendingRoleMap.remove(playerId);
        roleMap.remove(playerId);
        if (playerService == null) {
            return true;
        }
        RpcResult<Void> exitResult = PlayerMapRpcProxy.sendOnExitMap(playerService, playerId, sceneId);
        if (!exitResult.isSuccess()) {
            log.warn("StageService 通知 PlayerService 玩家退出地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, exitResult.getErrorCode(), exitResult.getErrorMessage());
        }
        return true;
    }

    public boolean isEmpty() {
        return pendingRoleMap.isEmpty() && roleMap.isEmpty();
    }

    public SMapKey getMapKey() {
        return new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
    }
}
