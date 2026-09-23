package org.evd.game.SceneManagerService.disconnect;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SceneManagerService 关联服务连接就绪后的 Stage 恢复逻辑。 */
@Actor
@Slf4j
public final class SceneManagerServiceConnectLogic {
    public void restoreStageMaps(CallPoint stage, List<SMapInfo> maps) {
        SceneManagerService owner = owner();
        if (stage == null || maps == null) {
            log.error("SceneManager 接收 Stage 地图快照参数非法: stage={}, maps={}", stage, maps);
            return;
        }
        boolean registered = owner.getNode().getRegisteredService(stage) != null;
        if (!registered) {
            log.error("SceneManager 拒绝未注册 Stage 的地图快照: stage={}, mapCount={}", stage, maps.size());
            return;
        }
        Set<SMapKey> mapKeys = new HashSet<>();
        for (SMapInfo map : maps) {
            if (map == null || map.getSceneId() <= 0L || map.getMapCfgId() <= 0
                    || !mapKeys.add(map.toMapKey())) {
                log.error("SceneManager 拒绝非法或重复的 Stage 地图快照: stage={}, maps={}", stage, maps);
                return;
            }
        }

        int disappeared = owner.sceneDealManager().replaceStageScenes(stage, maps);
        owner.updateStageMapCount(stage, maps.size());
        log.info("SceneManager 接收 Stage 地图快照完成: service={}, stage={}, disappearedMapCount={}, mapCount={}",
                owner.getId(), stage, disappeared, maps.size());
    }

    private SceneManagerService owner() {
        return Service.getCurrent(SceneManagerService.class);
    }
}
