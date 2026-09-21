package org.evd.game.StageService.disconnect;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.StageService;
import org.evd.game.StageService.mapCreate.StageSceneLogic;
import org.evd.game.StageService.scene.battle.BattleScene;
import org.evd.game.StageService.scene.movable.BattleRole;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.LocationService.LocationServiceRpcProxy;
import org.evd.game.common.proxy.SceneManagerService.SceneManagerServiceConnectRpcProxy;
import org.evd.game.common.serializeBean.LocationService.SLocationAddress;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** StageService 关联服务连接就绪后的本地恢复逻辑。 */
@Actor
@Slf4j
public final class StageServiceConnectLogic {
    /** LocationService 连接就绪后恢复 StageScene 的 Location 关联。 */
    public void onServiceConnectReady(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service == null || service.getServiceType() == null) {
                continue;
            }
            switch (service.getServiceType()) {
                case LOC -> onLocationServiceConnect(service.getCallPoint());
                case SCENE_MANAGER -> onSceneManagerConnect(service.getCallPoint());
                default -> {
                }
            }
        }
    }

    private void onSceneManagerConnect(CallPoint sceneManager) {
        StageService owner = owner();
        List<SMapInfo> maps = owner.getActor(StageSceneLogic.class).getMaps();
        RpcResult<Void> result = SceneManagerServiceConnectRpcProxy.sendRestoreStageMaps(
                sceneManager, owner.getCallPoint(), maps);
        if (!result.isSuccess()) {
            log.error("StageService 向 SceneManager 推送地图快照失败: service={}, sceneManager={}, mapCount={}, errorCode={}, message={}",
                    owner.getId(), sceneManager, maps.size(), result.getErrorCode(), result.getErrorMessage());
            return;
        }
        log.info("StageService 向 SceneManager 推送地图快照完成: service={}, sceneManager={}, mapCount={}",
                owner.getId(), sceneManager, maps.size());
    }

    public void onLocationServiceConnect(CallPoint locationService) {
        Map<Long, BattleScene> scenes = owner().getActor(StageSceneLogic.class).getScenes();
        log.info("StageService 开始处理 LocationService 重连: service={}, locationService={}, sceneCount={}",
                owner().getId(), locationService, scenes.size());
        List<SLocationAddress> addresses = new ArrayList<>();
        for (BattleScene scene : scenes.values()) {
            for (Map.Entry<Long, BattleRole> entry : scene.getRoleMap().entrySet()) {
                long playerId = entry.getKey();
                ActorAddress actorAddress = owner().getMapPlayerActorAddress(playerId);
                if (actorAddress != null) {
                    addresses.add(new SLocationAddress(ActorId.mapPlayer(playerId), actorAddress));
                }
            }
        }
        if (addresses.isEmpty()) {
            log.info("StageService 无须恢复 Location 地址: service={}, locationService={}",
                    owner().getId(), locationService);
            return;
        }
        RpcResult<Void> result = LocationServiceRpcProxy.sendAddBatch(locationService, addresses);
        if (!result.isSuccess()) {
            log.error("StageService 重新发送 MapPlayer ActorAddress 失败: service={}, locationService={}, requested={}, errorCode={}, message={}",
                    owner().getId(), locationService, addresses.size(), result.getErrorCode(), result.getErrorMessage());
            return;
        }
        log.info("StageService 重新发送 MapPlayer ActorAddress 完成: service={}, locationService={}, requested={}",
                owner().getId(), locationService, addresses.size());
    }

    private StageService owner() {
        return Service.getCurrent(StageService.class);
    }
}
