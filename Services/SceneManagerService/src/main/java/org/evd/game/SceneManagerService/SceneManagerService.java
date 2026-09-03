package org.evd.game.SceneManagerService;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.scene.SceneDealManager;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.runtime.util.id.SceneIdGenerator;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SceneManagerService 只负责 Deal 生命周期、场景 ID 和 Stage 路由。 */
@Slf4j
public class SceneManagerService extends Service {
    private final SceneDealManager sceneDealManager;
    private final Map<CallPoint, Integer> stageMapCounts = new HashMap<>();
    private static final long STAGE_MAP_COUNT_REFRESH_INTERVAL_MILLIS = 5_000L;

    public SceneManagerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        sceneDealManager = new SceneDealManager(this);
    }

    @Override
    public void init() {
        super.init();
        newRepeatedTimerCoroutine(STAGE_MAP_COUNT_REFRESH_INTERVAL_MILLIS, true,
                this::refreshStageMapCounts);
    }

    public SceneDealManager sceneDealManager() {
        return sceneDealManager;
    }

    public long createSceneId() {
        return SceneIdGenerator.nextId();
    }

    public CallPoint getSceneStage(long sceneId) {
        return sceneDealManager.getSceneStage(sceneId);
    }

    public CallPoint chooseStage() {
        List<RegisteredService> stages = new ArrayList<>(getNode().getServicesByType(ServiceType.STAGE));
        if (stages.isEmpty()) {
            throw new SysException("没有可用的 StageService");
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
            throw new SysException("没有可用的 StageService");
        }
        return selectedStage;
    }

    /** 定期读取所有 Stage 的地图数量，供 chooseStage 使用缓存。 */
    private void refreshStageMapCounts() {
        Map<CallPoint, Integer> latest = new HashMap<>();
        for (RegisteredService registeredStage : getNode().getServicesByType(ServiceType.STAGE)) {
            CallPoint stage = registeredStage.getCallPoint();
            RpcResult<Integer> result = StageServiceRpcProxy.callGetMapCount(stage);
            if (!result.isSuccess() || result.getValue() == null) {
                log.warn("SceneManager 刷新 Stage 地图数量失败: stage={}, errorCode={}, message={}",
                        stage, result.getErrorCode(), result.getErrorMessage());
                continue;
            }
            latest.put(stage, result.getValue());
        }
        stageMapCounts.clear();
        stageMapCounts.putAll(latest);
    }
}
