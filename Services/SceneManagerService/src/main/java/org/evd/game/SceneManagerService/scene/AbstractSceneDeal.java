package org.evd.game.SceneManagerService.scene;

import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 所有地图 Deal 的公共场景创建、排队和转发逻辑。 */
public abstract class AbstractSceneDeal {
    private final SceneManagerService owner;
    private final Map<SMapKey, SMSceneInfo> scenes = new HashMap<>();

    protected AbstractSceneDeal(SceneManagerService owner) {
        this.owner = owner;
    }

    public void enter(SMapEnterRequest request) {
        if (request == null || request.getPlayerId() <= 0L || request.getTargetInfo() == null
                || request.getTargetInfo().getMapCfgId() <= 0) {
            LogCore.core.warn("SceneManager 收到非法进入地图请求: request={}", request);
            return;
        }
        SMapInfo targetInfo = request.getTargetInfo();
        SMapKey mapKey = targetInfo.toMapKey();
        SMSceneInfo sceneInfo = scenes.get(mapKey);
        if (sceneInfo == null) {
            CallPoint stage = owner.chooseStage();
            sceneInfo = new SMSceneInfo(mapKey, owner.createSceneId(), stage);
            scenes.put(mapKey, sceneInfo);
            sceneInfo.getWaitEnterQueue().add(new SMapEnterRequest(request));
            createScene(sceneInfo);
            return;
        }

        if (sceneInfo.getState() == SceneState.CREATING) {
            sceneInfo.getWaitEnterQueue().add(new SMapEnterRequest(request));
            return;
        }
        if (sceneInfo.getState() == SceneState.CREATED) {
            sendPrepareEnter(sceneInfo, request);
        }
    }

    private void createScene(SMSceneInfo sceneInfo) {
        RpcResult<Boolean> result = StageServiceRpcProxy.callCreateScene(sceneInfo.getStageCallPoint(),
                sceneInfo.getMapKey(), sceneInfo.getSceneId());
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            sceneInfo.setState(SceneState.DESTROYED);
            scenes.remove(sceneInfo.getMapKey());
            LogCore.core.error("SceneManager 创建 SceneBattle 失败: sceneId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                    sceneInfo.getSceneId(), sceneInfo.getMapKey().getMapCfgId(), sceneInfo.getMapKey().getGroupId(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }

        sceneInfo.setState(SceneState.CREATED);
        List<SMapEnterRequest> requests = new ArrayList<>(sceneInfo.getWaitEnterQueue());
        sceneInfo.getWaitEnterQueue().clear();
        for (SMapEnterRequest request : requests) {
            sendPrepareEnter(sceneInfo, request);
        }
    }

    private void sendPrepareEnter(SMSceneInfo sceneInfo, SMapEnterRequest original) {
        SMapEnterRequest request = new SMapEnterRequest(original);
        SMapInfo targetInfo = request.getTargetInfo();
        if (targetInfo == null) {
            LogCore.core.error("SceneManager 预进入请求缺少目标地图: playerId={}, sceneId={}",
                    request.getPlayerId(), sceneInfo.getSceneId());
            return;
        }
        targetInfo.setSceneId(sceneInfo.getSceneId());
        request.setTargetInfo(targetInfo);
        RpcResult<Void> result = StageServiceRpcProxy.sendPrepareEnterScene(sceneInfo.getStageCallPoint(), request);
        if (!result.isSuccess()) {
            LogCore.core.error("SceneManager 发送 Stage 预进入请求失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    request.getPlayerId(), sceneInfo.getSceneId(), result.getErrorCode(), result.getErrorMessage());
        }
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
