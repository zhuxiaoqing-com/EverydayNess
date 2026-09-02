package org.evd.game.SceneManagerService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.exception.SysException;

/** SceneManager 独立的地图分发逻辑。 */
    @Actor
public final class SceneManagerLogic {
    public void enterMap(SMapEnterRequest request) {
        if (request == null || request.getTargetInfo() == null) {
            throw new SysException("进入地图请求缺少目标地图");
        }
        owner().sceneDealManager().getDeal(request.getTargetInfo().getMapCfgId()).enter(request);
    }

    private SceneManagerService owner() {
        return Service.getCurrent(SceneManagerService.class);
    }
}
