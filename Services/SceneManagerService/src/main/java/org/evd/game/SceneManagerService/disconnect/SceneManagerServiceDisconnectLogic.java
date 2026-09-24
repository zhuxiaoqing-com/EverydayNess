package org.evd.game.SceneManagerService.disconnect;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.SceneManagerService.routing.SceneManagerRoutingLogic;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

/** SceneManagerService 关联服务断开后的 Stage 路由清理逻辑。 */
@Actor
@Slf4j
public final class SceneManagerServiceDisconnectLogic {
    public void onServiceDisconnect(Collection<RegisteredService> services) {
        SceneManagerService owner = owner();
        log.info("SceneManager 开始处理关联服务断开: service={}, count={}", owner.getId(), services.size());
        for (RegisteredService service : services) {
            if (service == null || service.getServiceType() != ServiceType.STAGE) {
                continue;
            }
            CallPoint stage = service.getCallPoint();
            owner.getActor(SceneManagerRoutingLogic.class).removeStageMapCount(stage);
            owner.sceneDealManager().onStageServiceDisconnect(stage);
            log.info("SceneManager 清理 Stage 场景路由: service={}, stage={}", owner.getId(), stage);
        }
        log.info("SceneManager 完成关联服务断开处理: service={}, count={}", owner.getId(), services.size());
    }

    private SceneManagerService owner() {
        return Service.getCurrent(SceneManagerService.class);
    }
}
