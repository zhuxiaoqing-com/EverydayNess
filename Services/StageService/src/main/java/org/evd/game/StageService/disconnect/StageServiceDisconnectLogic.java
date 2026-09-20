package org.evd.game.StageService.disconnect;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.StageService;
import org.evd.game.StageService.mapCreate.StageSceneLogic;
import org.evd.game.StageService.scene.battle.BattleScene;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;
import java.util.Map;

/** StageService 关联服务断开后的本地收敛逻辑。 */
@Actor
@Slf4j
public final class StageServiceDisconnectLogic {
    /** PlayerService 断开时清理该 PlayerService 在本 Stage 的玩家场景。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        StageService owner = owner();
        LogCore.core.info("StageService 开始处理关联服务断开: service={}, count={}", owner.getId(), serviceList.size());
        for (RegisteredService service : serviceList) {
            if (service != null && service.getServiceType() == ServiceType.PLAYER) {
                onPlayerServiceDisconnect(service.getCallPoint());
            }
        }
        LogCore.core.info("StageService 完成关联服务断开处理: service={}, count={}", owner.getId(), serviceList.size());
    }

    public void onPlayerServiceDisconnect(CallPoint playerService) {
        Map<Long, BattleScene> scenes = owner().getActor(StageSceneLogic.class).getScenes();
        log.info("StageService 开始处理 PlayerService 断开: service={}, playerService={}, sceneCount={}",
                owner().getId(), playerService, scenes.size());
        for (BattleScene scene : scenes.values()) {
            scene.onPlayerServiceDisconnect(playerService);
        }
        log.info("StageService 完成 PlayerService 断开处理: service={}, playerService={}, sceneCount={}",
                owner().getId(), playerService, scenes.size());
    }


    private StageService owner() {
        return Service.getCurrent(StageService.class);
    }
}
