package org.evd.game.StageService.mapCreate;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.StageService;
import org.evd.game.StageService.scene.battle.BattleScene;
import org.evd.game.StageService.scene.battle.CampScene;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.config.table.MapConfig;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.constant.MapConst;
import org.evd.game.common.proxy.PlayerService.PlayerMapRpcProxy;
import org.evd.game.common.proxy.SceneManagerService.SceneManagerRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.*;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** StageService 的地图创建和进入业务逻辑。 */
@Slf4j
@Actor
public final class StageSceneLogic {
    private final Map<Long, BattleScene> scenes = new HashMap<>();
    private final Map<SMapKey, Long> sceneIds = new HashMap<>();

    public List<SMapInfo> getMaps() {
        return scenes.values().stream().map(scene -> new SMapInfo(scene.getSceneId(),
                scene.getMapKey().getMapCfgId(), scene.getMapKey().getGroupId())).toList();
    }

    /** 返回当前 Stage 已创建的地图数量，供 SceneManager 做负载均衡。 */
    public int getMapCount() {
        return scenes.size();
    }

    public void tick(long now) {
        scenes.values().forEach(scene -> scene.tick(now));
    }

    public BattleScene getScene(long sceneId) {
        return scenes.get(sceneId);
    }

    public SRunningMapInfo getRunningMapInfo(long sceneId) {
        BattleScene scene = scenes.get(sceneId);
        return scene == null ? null : scene.getRunningMapInfo();
    }

    /** 创建 BattleScene；相同 sceneId 重复创建时保持幂等。 */
    public boolean createScene(SMapKey mapKey, long sceneId) {
        return createScene(new SMapCreateRequest(mapKey, null), sceneId);
    }

    public boolean createScene(SMapCreateRequest request, long sceneId) {
        if (request == null || request.getMapKey() == null
                || sceneId <= 0L) {
            return false;
        }
        SMapKey mapKey = request.getMapKey();
        if (mapKey.getMapCfgId() <= 0) {
            return false;
        }
        MapConfig mapConfig = MapConfigs.get(mapKey.getMapCfgId());
        if (mapConfig == null) {
            log.error("StageService 创建地图时找不到地图配置: mapCfgId={}, sceneId={}",
                    mapKey.getMapCfgId(), sceneId);
            return false;
        }
        BattleScene current = scenes.get(sceneId);
        if (current != null) {
            if (!current.getMapKey().equals(mapKey)) {
                log.error("StageService 同一个 sceneId 对应了不同的 SMapKey: sceneId={}, currentMapCfgId={}, currentGroupId={}, requestMapCfgId={}, requestGroupId={}",
                        sceneId, current.getMapKey().getMapCfgId(), current.getMapKey().getGroupId(),
                        mapKey.getMapCfgId(), mapKey.getGroupId());
                throw new IllegalStateException("同一个 sceneId 对应了不同的 SMapKey: sceneId=" + sceneId);
            }
            return true;
        }
        Long oldSceneId = sceneIds.get(mapKey);
        if (oldSceneId != null) {
            BattleScene oldScene = scenes.get(oldSceneId);
            if (oldScene == null) {
                log.error("StageService sceneIds 存在映射但 scenes 找不到 BattleScene: sceneId={}, mapCfgId={}, groupId={}",
                        oldSceneId, mapKey.getMapCfgId(), mapKey.getGroupId());
                throw new IllegalStateException("sceneIds 存在映射但 scenes 找不到 BattleScene: sceneId=" + oldSceneId);
            }
            return oldSceneId == sceneId;
        }
        BattleScene createdScene = createSceneObject(request, sceneId, mapConfig.getType());
        scenes.put(sceneId, createdScene);
        sceneIds.put(mapKey, sceneId);
        return true;
    }

    private BattleScene createSceneObject(SMapCreateRequest request, long sceneId, int mapType) {
        if (mapType == MapConst.MapType.NORMAL.getType()) {
            return new BattleScene(request.getMapKey(), sceneId, owner());
        }
        if (mapType == MapConst.MapType.MULTI_MATCH.getType()
                || mapType == MapConst.MapType.CAMP.getType()) {
            return new CampScene(request.getMapKey(), sceneId, owner(), request.getMatchParams());
        }
        throw new IllegalArgumentException("不支持的地图类型: " + mapType);
    }

    /** 进入目标 BattleScene 的预加入流程，然后退出旧场景。 */
    public boolean prepareEnterScene(PlayerEnterRequest request) {
        if (request == null || request.getTargetInfo() == null) {
            log.warn("StageService 收到空的预进入请求");
            return false;
        }
        SMapInfo targetInfo = request.getTargetInfo();
        Long sceneId = sceneIds.get(targetInfo.toMapKey());
        BattleScene scene = sceneId == null ? null : scenes.get(sceneId);
        if (scene == null) {
            log.warn("StageService 根据 SMapKey 找不到目标 BattleScene: playerId={}, sceneId={}, mapCfgId={}, groupId={}",
                    request.getPlayerId(), targetInfo.getSceneId(), targetInfo.getMapCfgId(), targetInfo.getGroupId());
            return false;
        }
        if (!scene.canEnter(request)) {
            return false;
        }
        scene.addPendingRole(request);
        log.info("StageService 玩家加入预进入队列: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}",
                request.getPlayerId(), request.getTransferId(), targetInfo.getSceneId(),
                targetInfo.getMapCfgId(), targetInfo.getGroupId());

        SMapInfo oldMapInfo = request.getOldMapInfo();
        if (oldMapInfo != null && oldMapInfo.getSceneId() > 0L
                && oldMapInfo.getSceneId() != targetInfo.getSceneId()) {
            CallPoint sceneManager = MapConst.getSceneManagerCallPoint(oldMapInfo.getMapCfgId());
            RpcResult<Boolean> exitResult = SceneManagerRpcProxy.callExitMap(sceneManager, oldMapInfo,
                    request.getPlayerId());
            if (!exitResult.isSuccess() || !Boolean.TRUE.equals(exitResult.getValue())) {
                scene.cancelPending(request);
                log.error("StageService 请求 SceneManager 退出旧地图失败: playerId={}, oldSceneId={}, errorCode={}, message={}",
                        request.getPlayerId(), oldMapInfo.getSceneId(), exitResult.getErrorCode(),
                        exitResult.getErrorMessage());
                return false;
            }
            log.info("StageService 玩家旧地图退出成功: playerId={}, transferId={}, oldSceneId={}, targetSceneId={}",
                    request.getPlayerId(), request.getTransferId(), oldMapInfo.getSceneId(), targetInfo.getSceneId());
        }

        if (!scene.hasPendingRole(request)) {
            log.warn("预进入玩家已因对端失联被移除: playerId={}, transferId={}",
                    request.getPlayerId(), request.getTransferId());
            return false;
        }
        RpcResult<Boolean> readyResult = PlayerMapRpcProxy.callReadyEnterMap(request.getPlayerService(),
                request.getPlayerId(), request.getTransferId(), targetInfo);
        if (!readyResult.isSuccess() || !Boolean.TRUE.equals(readyResult.getValue())) {
            scene.cancelPending(request);
            log.error("StageService 通知 PlayerService 客户端加载地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    request.getPlayerId(), targetInfo.getSceneId(), readyResult.getErrorCode(), readyResult.getErrorMessage());
            return false;
        }
        log.info("StageService 已通知 PlayerService 客户端加载地图: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}",
                request.getPlayerId(), request.getTransferId(), targetInfo.getSceneId(),
                targetInfo.getMapCfgId(), targetInfo.getGroupId());
        return true;
    }

    /** 客户端完成加载后，正式进入 SceneBattle。 */
    public void enterScene(long sceneId, SPlayerMapData playerData) {
        BattleScene scene = scenes.get(sceneId);
        if (scene != null) {
            scene.roleEnter(playerData);
        }
    }

    /** 退出一个场景中的正式玩家或预加入玩家。 */
    public boolean exitScene(long sceneId, long playerId) {
        BattleScene scene = scenes.get(sceneId);
        return scene == null || scene.roleExit(playerId);
    }

    /** 销毁空场景。 */
    public boolean destroyScene(long sceneId) {
        BattleScene scene = scenes.get(sceneId);
        if (scene == null) {
            return true;
        }
        if (!scene.isEmpty()) {
            return false;
        }
        scenes.remove(sceneId);
        sceneIds.remove(scene.getMapKey());
        return true;
    }

    public Map<Long, BattleScene> getScenes() {
        return scenes;
    }

    private StageService owner() {
        return Service.getCurrent(StageService.class);
    }
}
