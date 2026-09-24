package org.evd.game.SceneManagerService.routing;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SceneManagerService 的 Stage 负载选择逻辑。 */
@Actor
@Slf4j
public final class SceneManagerRoutingLogic {
    private final Map<CallPoint, Integer> stageMapCounts = new HashMap<>();

    public void removeStageMapCount(CallPoint stage) {
        stageMapCounts.remove(stage);
    }

    public void updateStageMapCount(CallPoint stage, int mapCount) {
        stageMapCounts.put(stage, mapCount);
    }

    /** 定期读取所有 Stage 的地图数量，供 chooseStage 使用缓存。 */
    public void refreshStageMapCounts() {
        SceneManagerService owner = owner();
        Map<CallPoint, Integer> latest = new HashMap<>();
        for (RegisteredService registeredStage : owner.getServicesByType(ServiceType.STAGE)) {
            CallPoint stage = registeredStage.getCallPoint();
            RpcResult<Integer> result = StageServiceRpcProxy.callGetMapCount(stage);
            if (!result.isSuccess() || result.getValue() == null) {
                log.warn("SceneManager 刷新 Stage 地图数量失败: stage={}, errorCode={}, message={}",
                        stage, result.getErrorCode(), result.getErrorMessage());
                continue;
            }
            latest.put(stage, result.getValue());
        }
        // 拉取地图数量期间会挂起协程，不能把期间断开的 Stage 重新写回候选。
        latest.keySet().retainAll(owner.getCallPointByType(ServiceType.STAGE));
        latest.keySet().removeIf(a -> !Service.checkSyncDataValid(a));
        stageMapCounts.clear();
        stageMapCounts.putAll(latest);
    }

    public CallPoint chooseStage() {
        List<RegisteredService> stages = new ArrayList<>(owner().getServicesByType(ServiceType.STAGE));
        if (stages.isEmpty()) {
            return null;
        }

        CallPoint selectedStage = null;
        int selectedMapCount = Integer.MAX_VALUE;
        for (RegisteredService registeredStage : stages) {
            CallPoint stage = registeredStage.getCallPoint();
            Integer mapCount = stageMapCounts.get(stage);
            if (mapCount == null) {
                log.warn("SceneManager 没有 Stage 地图数量缓存: stage={}", stage);
                continue;
            }
            if (selectedStage == null || mapCount < selectedMapCount) {
                selectedStage = new CallPoint(stage);
                selectedMapCount = mapCount;
            }
        }
        if (selectedStage == null) {
            return null;
        }
        return selectedStage;
    }

    private SceneManagerService owner() {
        return Service.getCurrent(SceneManagerService.class);
    }
}
