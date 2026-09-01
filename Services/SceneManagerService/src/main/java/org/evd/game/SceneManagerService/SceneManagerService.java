package org.evd.game.SceneManagerService;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MapRoute;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SceneManagerService extends Service {
    private final Map<Integer, MapRoute> maps = new HashMap<>();
    private final Map<Integer, CompletableFuture<MapRoute>> creatingMaps = new HashMap<>();
    private long nextMapInstanceId = 1L;
    private int nextStageIndex;

    public SceneManagerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    /** 获取共享地图实例；同一配置地图只创建一个实例。 */
    public MapRoute acquireMap(int mapConfigId) {
        if (mapConfigId <= 0) {
            throw new SysException("地图配置 ID 非法: mapConfigId={}", mapConfigId);
        }
        MapRoute active = maps.get(mapConfigId);
        if (active != null) {
            return copy(active);
        }

        CompletableFuture<MapRoute> creating = creatingMaps.get(mapConfigId);
        if (creating != null) {
            return copy(awaitCompletionStage(creating, getCallWaitTimeoutInternal()));
        }

        creating = new CompletableFuture<>();
        creatingMaps.put(mapConfigId, creating);
        try {
            CallPoint stage = chooseStage();
            long mapInstanceId = nextMapInstanceId++;
            RpcResult<Boolean> result = StageServiceRpcProxy.callCreateMap(stage, mapInstanceId, mapConfigId);
            if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
                throw new SysException("Stage 创建地图失败: mapConfigId={}, mapInstanceId={}, errorCode={}, message={}",
                        mapConfigId, mapInstanceId, result.getErrorCode(), result.getErrorMessage());
            }
            MapRoute route = new MapRoute(mapConfigId, mapInstanceId, stage);
            maps.put(mapConfigId, route);
            creating.complete(route);
            return copy(route);
        } catch (RuntimeException e) {
            creating.completeExceptionally(e);
            throw e;
        } finally {
            creatingMaps.remove(mapConfigId, creating);
        }
    }

    private CallPoint chooseStage() {
        List<RegisteredService> stages = new ArrayList<>(getNode().getServicesByType(ServiceType.STAGE));
        if (stages.isEmpty()) {
            throw new SysException("没有可用的 StageService");
        }
        CallPoint stage = stages.get(nextStageIndex++ % stages.size()).getCallPoint();
        return new CallPoint(stage);
    }

    private MapRoute copy(MapRoute route) {
        return new MapRoute(route.getMapConfigId(), route.getMapInstanceId(), route.getStage());
    }
}
