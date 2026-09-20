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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;

/** SceneManagerService 只负责 Deal 生命周期、场景 ID 和 Stage 路由。 */
@Slf4j
public class SceneManagerService extends Service {
    private final SceneDealManager sceneDealManager;
    private final Map<CallPoint, Integer> stageMapCounts = new HashMap<>();
    private final Set<CallPoint> recoveredStages = new HashSet<>();

    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> services) {
        log.info("SceneManager 开始处理关联服务断开: service={}, count={}", getId(), services.size());
        for (RegisteredService service : services) {
            if (service.getServiceType() == ServiceType.STAGE) {
                CallPoint stage = service.getCallPoint();
                recoveredStages.remove(stage);
                stageMapCounts.remove(stage);
                sceneDealManager.onStageServiceDisconnect(stage);
                log.info("SceneManager 清理 Stage 场景路由: service={}, stage={}", getId(), stage);
            }
        }
    }

    @Override
    protected void onServiceConnectReady(Collection<RegisteredService> services) {
        for (RegisteredService service : services) {
            if (service.getServiceType() == ServiceType.STAGE) {
                restoreStageSnapshot(service);
            }
        }
        log.info("SceneManager 完成 Stage 场景恢复触发: service={}, count={}", getId(), services.size());
    }

    private void restoreStageSnapshot(RegisteredService service) {
        CallPoint stage = service.getCallPoint();
        RpcResult<List<SMapInfo>> result = StageServiceRpcProxy.callGetMaps(stage);
        if (!result.isSuccess() || result.getValue() == null) {
            log.error("SceneManager 恢复 Stage 地图失败: stage={}, error={}", stage, result.getErrorMessage());
            return;
        }
        // RPC 挂起期间对端可能已离线，不能将迟到快照重新写回。
        if (getNode().getServicesByType(ServiceType.STAGE).stream()
                .noneMatch(current -> stage.equals(current.getCallPoint()))) {
            return;
        }
        for (SMapInfo map : result.getValue()) {
            sceneDealManager.restoreScene(stage, map);
        }
        stageMapCounts.put(stage, result.getValue().size());
        recoveredStages.add(stage);
        log.info("SceneManager Stage 场景恢复完成: service={}, stage={}, mapCount={}",
                getId(), stage, result.getValue().size());
    }

    /** 已发现但尚未恢复的 Stage 可能仍持有旧地图，不能重复创建。 */
    public void requireStageRecoveryComplete() {
        for (RegisteredService stage : getNode().getServicesByType(ServiceType.STAGE)) {
            if (!recoveredStages.contains(stage.getCallPoint())) {
                throw new SysException("Stage 地图尚未恢复: " + stage.getCallPoint());
            }
        }
    }
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
            if (!recoveredStages.contains(stage)) {
                continue;
            }
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
            if (!recoveredStages.contains(stage)) {
                restoreStageSnapshot(registeredStage);
                if (recoveredStages.contains(stage)) {
                    latest.put(stage, stageMapCounts.get(stage));
                }
                continue;
            }
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
