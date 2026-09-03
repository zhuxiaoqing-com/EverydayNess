package org.evd.game.SceneManagerService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.exception.SysException;

/** SceneManager 独立的地图分发逻辑。 */
    @Actor
public final class SceneManagerLogic {
    public boolean enterMap(SMapEnterRequest request) {
        if (request == null || request.getTargetInfo() == null) {
            throw new SysException("进入地图请求缺少目标地图");
        }
        return owner().sceneDealManager().getDeal(request.getTargetInfo().getMapCfgId()).enter(request);
    }

    public boolean exitMap(SMapInfo mapInfo, long playerId) {
        if (mapInfo == null) {
            throw new SysException("退出地图请求缺少地图信息");
        }
        return owner().sceneDealManager().getDeal(mapInfo.getMapCfgId()).exitMap(mapInfo.toMapKey(), playerId);
    }

    private SceneManagerService owner() {
        return Service.getCurrent(SceneManagerService.class);
    }
}
