package org.evd.game.SceneManagerService.disconnect;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** SceneManagerService 关联服务连接就绪后的 Stage 恢复逻辑。 */
@Actor
@Slf4j
public final class SceneManagerServiceConnectLogic {
    public void restoreStageMaps(CallPoint stage, java.util.List<SMapInfo> maps) {
        SceneManagerService owner = owner();
        if (stage == null || maps == null) {
            log.error("SceneManager 接收 Stage 地图快照参数非法: stage={}, maps={}", stage, maps);
            return;
        }
        for (SMapInfo map : maps) {
            owner.sceneDealManager().restoreScene(stage, map);
        }
        owner.updateStageMapCount(stage, maps.size());
        log.info("SceneManager 接收 Stage 地图快照完成: service={}, stage={}, mapCount={}",
                owner.getId(), stage, maps.size());
    }

    private SceneManagerService owner() {
        return Service.getCurrent(SceneManagerService.class);
    }
}
