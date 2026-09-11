package org.evd.game.SceneManagerService.scene;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.constant.MapConst;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** 所有地图 Deal 的公共场景创建和转发逻辑。 */
@Slf4j
public abstract class AbstractSceneDeal {
    private final SceneManagerService owner;
    private final Map<SMapKey, SMSceneInfo> scenes = new HashMap<>();

    protected AbstractSceneDeal(SceneManagerService owner) {
        this.owner = owner;
    }

    public boolean enter(PlayerEnterRequest request) {
        if (request == null || request.getPlayerId() <= 0L || request.getTargetInfo() == null
                || request.getTargetInfo().getMapCfgId() <= 0) {
            log.warn("SceneManager 收到非法进入地图请求: request={}", request);
            return false;
        }
        SMapInfo targetInfo = request.getTargetInfo();
        SMapKey mapKey = targetInfo.toMapKey();
        log.info("SceneManager 收到地图进入请求: playerId={}, transferId={}, targetInfo={}, oldMapInfo={}",
                request.getPlayerId(), request.getTransferId(), targetInfo, request.getOldMapInfo());
        SMSceneInfo sceneInfo = scenes.get(mapKey);
        boolean needCreate = sceneInfo == null;
        boolean wasCreating = sceneInfo != null && sceneInfo.getState() == SMSceneState.CREATING;
        if (!needCreate && !wasCreating) {
            return sendPrepareEnter(sceneInfo, request);
        }

        boolean sceneExistedBeforeLock = sceneInfo != null;
        if (needCreate) {
            CallPoint stage = owner.chooseStage();
            sceneInfo = new SMSceneInfo(mapKey, owner.createSceneId(), stage);
            scenes.put(mapKey, sceneInfo);
            log.info("SceneManager 创建场景记录: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}, stage={}",
                    request.getPlayerId(), request.getTransferId(), sceneInfo.getSceneId(),
                    mapKey.getMapCfgId(), mapKey.getGroupId(), stage);
        }
        sceneInfo.getWaitEnterQueue().put(request.getPlayerId(), request);
        log.info("SceneManager 玩家加入场景等待队列: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}, state={}",
                request.getPlayerId(), request.getTransferId(), sceneInfo.getSceneId(),
                mapKey.getMapCfgId(), mapKey.getGroupId(), sceneInfo.getState());

        try (ContinuationLockScope ignored = owner.awaitCoroutineLockScope(LockType.MAP_SCENE, mapKey)) {
            sceneInfo = scenes.get(mapKey);
            if (sceneInfo == null) {
                if (sceneExistedBeforeLock) {
                    log.error("SceneManager 获取创建锁后场景消失，取消进入请求: playerId={}, transferId={}, mapCfgId={}, groupId={}",
                            request.getPlayerId(), request.getTransferId(), mapKey.getMapCfgId(), mapKey.getGroupId());
                    return false;
                }
                log.error("SceneManager 获取创建锁后场景仍不存在，取消进入请求: playerId={}, transferId={}, mapCfgId={}, groupId={}",
                        request.getPlayerId(), request.getTransferId(), mapKey.getMapCfgId(), mapKey.getGroupId());
                return false;
            }
            if (sceneInfo.getWaitEnterQueue().get(request.getPlayerId()) != request) {
                log.error("SceneManager 玩家进入等待数据已失效，取消进入请求: playerId={}, transferId={}, mapCfgId={}, groupId={}",
                        request.getPlayerId(), request.getTransferId(), mapKey.getMapCfgId(), mapKey.getGroupId());
                return false;
            }
            if (needCreate && !createStageScene(sceneInfo, new SMapCreateRequest(
                    mapKey, null), sceneInfo.getSceneId())) {
                return false;
            }
            if (sceneInfo.getWaitEnterQueue().get(request.getPlayerId()) != request) {
                log.error("SceneManager 玩家创建场景后已不在进入等待数据中，取消进入请求: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}",
                        request.getPlayerId(), request.getTransferId(), sceneInfo.getSceneId(),
                        mapKey.getMapCfgId(), mapKey.getGroupId());
                return false;
            }
            sceneInfo.getWaitEnterQueue().remove(request.getPlayerId(), request);
        }

        if (sceneInfo.getState() != SMSceneState.CREATED) {
            log.error("SceneManager 场景未创建完成，无法发送预进入请求: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}, state={}",
                    request.getPlayerId(), request.getTransferId(), sceneInfo.getSceneId(),
                    mapKey.getMapCfgId(), mapKey.getGroupId(), sceneInfo.getState());
            return false;
        }
        return sendPrepareEnter(sceneInfo, request);
    }

    public SMapInfo createScene(SMapCreateRequest request) {
        if (request == null || request.getMapKey() == null) {
            log.error("SceneManager 收到非法地图创建请求: request={}", request);
            return null;
        }
        SMapKey mapKey = request.getMapKey();
        SMSceneInfo current = scenes.get(mapKey);
        if (current != null) {
            log.error("SceneManager 地图已存在，拒绝重复创建: sceneId={}, mapCfgId={}, groupId={}, state={}",
                    current.getSceneId(), mapKey.getMapCfgId(), mapKey.getGroupId(), current.getState());
            return null;
        }
        long sceneId = owner.createSceneId();
        SMSceneInfo sceneInfo = new SMSceneInfo(mapKey, sceneId, owner.chooseStage());
        scenes.put(mapKey, sceneInfo);
        try (ContinuationLockScope ignored = owner.awaitCoroutineLockScope(LockType.MAP_SCENE, mapKey)) {
            /*
             * sceneInfo 在加锁前已经写入 scenes，同一个 mapKey 又使用同一把创建锁，
             * 当前流程理论上不会出现记录被删除或替换的情况。
             */
            SMSceneInfo currentSceneInfo = scenes.get(mapKey);
            if (currentSceneInfo != sceneInfo) {
                log.error("SceneManager 获取创建锁后地图创建记录不一致，取消创建: newSceneInfo={}, oldSceneInfo={}",
                        sceneInfo, currentSceneInfo);
                return null;
            }
            SMapCreateRequest stageRequest = new SMapCreateRequest(mapKey,
                    request.getMatchParams());
            if (!createStageScene(sceneInfo, stageRequest, sceneId)) {
                log.error("SceneManager Stage 创建地图失败，取消创建: sceneId={}, mapCfgId={}, groupId={}",
                        sceneId, mapKey.getMapCfgId(), mapKey.getGroupId());
                return null;
            }
        }
        return new SMapInfo(sceneInfo.getSceneId(), mapKey.getMapCfgId(), mapKey.getGroupId());
    }

    /** 退出地图；优先取消仍在进入等待队列的请求，否则退出 Stage 中的玩家。 */
    public boolean exitMap(SMapKey mapKey, long playerId) {
        if (mapKey == null || playerId <= 0L) {
            return false;
        }
        SMSceneInfo sceneInfo = scenes.get(mapKey);
        if (sceneInfo == null) {
            return true;
        }
        if (sceneInfo.getWaitEnterQueue().remove(playerId) != null) {
            log.warn("SceneManager 退出地图时移除了仍在进入等待队列的玩家: playerId={}, sceneId={}, mapCfgId={}, groupId={}",
                    playerId, sceneInfo.getSceneId(), mapKey.getMapCfgId(), mapKey.getGroupId());
            return true;
        }
        if (sceneInfo.getState() != SMSceneState.CREATED) {
            log.error("SceneManager 场景未创建完成，无法退出地图: playerId={}, sceneId={}, mapCfgId={}, groupId={}, state={}",
                    playerId, sceneInfo.getSceneId(), mapKey.getMapCfgId(), mapKey.getGroupId(), sceneInfo.getState());
            return false;
        }
        RpcResult<Boolean> result = StageServiceRpcProxy.callExitScene(sceneInfo.getStageCallPoint(),
                sceneInfo.getSceneId(), playerId);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.error("SceneManager 退出 Stage 地图失败: playerId={}, sceneId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                    playerId, sceneInfo.getSceneId(), mapKey.getMapCfgId(), mapKey.getGroupId(),
                    result.getErrorCode(), result.getErrorMessage());
            return false;
        }
        log.info("SceneManager 退出 Stage 地图成功: playerId={}, sceneId={}, mapCfgId={}, groupId={}",
                playerId, sceneInfo.getSceneId(), mapKey.getMapCfgId(), mapKey.getGroupId());
        return true;
    }

    private boolean createStageScene(SMSceneInfo sceneInfo, SMapCreateRequest request, long sceneId) {
        RpcResult<Boolean> result = StageServiceRpcProxy.callCreateScene(
                sceneInfo.getStageCallPoint(), request, sceneId);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            sceneInfo.setState(SMSceneState.DESTROYED);
            scenes.remove(sceneInfo.getMapKey());
            log.error("SceneManager 创建 SceneBattle 失败: sceneId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                    sceneInfo.getSceneId(), sceneInfo.getMapKey().getMapCfgId(), sceneInfo.getMapKey().getGroupId(),
                    result.getErrorCode(), result.getErrorMessage());
            return false;
        }

        sceneInfo.setState(SMSceneState.CREATED);
        log.info("SceneManager 创建场景成功: sceneId={}, mapCfgId={}, groupId={}, stage={}",
                sceneInfo.getSceneId(), sceneInfo.getMapKey().getMapCfgId(),
                sceneInfo.getMapKey().getGroupId(), sceneInfo.getStageCallPoint());
        return true;
    }

    private boolean sendPrepareEnter(SMSceneInfo sceneInfo, PlayerEnterRequest request) {
        SMapInfo targetInfo = request.getTargetInfo();
        if (targetInfo == null) {
            log.error("SceneManager 预进入请求缺少目标地图: playerId={}, sceneId={}",
                    request.getPlayerId(), sceneInfo.getSceneId());
            return false;
        }
        targetInfo.setSceneId(sceneInfo.getSceneId());
        request.setTargetInfo(targetInfo);
        RpcResult<Boolean> result = StageServiceRpcProxy.callPrepareEnterScene(sceneInfo.getStageCallPoint(), request);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.error("SceneManager 发送 Stage 预进入请求失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    request.getPlayerId(), sceneInfo.getSceneId(), result.getErrorCode(), result.getErrorMessage());
            return false;
        }
        log.info("SceneManager 已发送 Stage 预进入请求: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}",
                request.getPlayerId(), request.getTransferId(), sceneInfo.getSceneId(),
                targetInfo.getMapCfgId(), targetInfo.getGroupId());
        return true;
    }

    public CallPoint findSceneStage(long sceneId) {
        for (SMSceneInfo info : scenes.values()) {
            if (info.getSceneId() == sceneId && info.getState() == SMSceneState.CREATED) {
                return info.getStageCallPoint();
            }
        }
        return null;
    }

    public List<SRunningMapInfo> getRunningMaps(int mapCfgId) {
        List<SRunningMapInfo> result = new ArrayList<>();
        for (SMSceneInfo sceneInfo : scenes.values()) {
            if (sceneInfo.getState() != SMSceneState.CREATED) continue;
            if (sceneInfo.getMapKey().getMapCfgId() != mapCfgId) continue;
            RpcResult<SRunningMapInfo> room = StageServiceRpcProxy.callGetRunningMapInfo(
                    sceneInfo.getStageCallPoint(), sceneInfo.getSceneId());
            if (!room.isSuccess() || room.getValue() == null) {
                log.error("SceneManager 查询运行中地图失败: sceneId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                        sceneInfo.getSceneId(), sceneInfo.getMapKey().getMapCfgId(),
                        sceneInfo.getMapKey().getGroupId(), room.getErrorCode(), room.getErrorMessage());
                continue;
            }
            result.add(room.getValue());
        }
        return result;
    }

}
