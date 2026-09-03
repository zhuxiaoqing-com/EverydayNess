package org.evd.game.SceneManagerService.scene;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.HashMap;
import java.util.Map;

/** 所有地图 Deal 的公共场景创建和转发逻辑。 */
@Slf4j
public abstract class AbstractSceneDeal {
    private final SceneManagerService owner;
    private final Map<SMapKey, SMSceneInfo> scenes = new HashMap<>();

    protected AbstractSceneDeal(SceneManagerService owner) {
        this.owner = owner;
    }

    public boolean enter(SMapEnterRequest request) {
        if (request == null || request.getPlayerId() <= 0L || request.getTargetInfo() == null
                || request.getTargetInfo().getMapCfgId() <= 0) {
            log.warn("SceneManager 收到非法进入地图请求: request={}", request);
            return false;
        }
        SMapInfo targetInfo = request.getTargetInfo();
        SMapKey mapKey = targetInfo.toMapKey();
        SMSceneInfo sceneInfo = scenes.get(mapKey);
        boolean needCreate = sceneInfo == null;
        boolean wasCreating = sceneInfo != null && sceneInfo.getState() == SceneState.CREATING;
        if (!needCreate && !wasCreating) {
            return sendPrepareEnter(sceneInfo, request);
        }

        boolean sceneExistedBeforeLock = sceneInfo != null;
        if (needCreate) {
            CallPoint stage = owner.chooseStage();
            sceneInfo = new SMSceneInfo(mapKey, owner.createSceneId(), stage);
            scenes.put(mapKey, sceneInfo);
        }
        sceneInfo.getWaitEnterQueue().put(request.getPlayerId(), request);

        try (ContinuationLockScope ignored = owner.awaitCoroutineLockScope(LockType.ACTOR, mapKey)) {
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
            if (needCreate && !createScene(sceneInfo)) {
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

        if (sceneInfo.getState() != SceneState.CREATED) {
            log.error("SceneManager 场景未创建完成，无法发送预进入请求: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}, state={}",
                    request.getPlayerId(), request.getTransferId(), sceneInfo.getSceneId(),
                    mapKey.getMapCfgId(), mapKey.getGroupId(), sceneInfo.getState());
            return false;
        }
        return sendPrepareEnter(sceneInfo, request);
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
        if (sceneInfo.getState() != SceneState.CREATED) {
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
        return true;
    }

    private boolean createScene(SMSceneInfo sceneInfo) {
        RpcResult<Boolean> result = StageServiceRpcProxy.callCreateScene(sceneInfo.getStageCallPoint(),
                sceneInfo.getMapKey(), sceneInfo.getSceneId());
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            sceneInfo.setState(SceneState.DESTROYED);
            scenes.remove(sceneInfo.getMapKey());
            log.error("SceneManager 创建 SceneBattle 失败: sceneId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                    sceneInfo.getSceneId(), sceneInfo.getMapKey().getMapCfgId(), sceneInfo.getMapKey().getGroupId(),
                    result.getErrorCode(), result.getErrorMessage());
            return false;
        }

        sceneInfo.setState(SceneState.CREATED);
        return true;
    }

    private boolean sendPrepareEnter(SMSceneInfo sceneInfo, SMapEnterRequest request) {
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
        return true;
    }

    public CallPoint findSceneStage(long sceneId) {
        for (SMSceneInfo info : scenes.values()) {
            if (info.getSceneId() == sceneId && info.getState() == SceneState.CREATED) {
                return info.getStageCallPoint();
            }
        }
        return null;
    }

}
