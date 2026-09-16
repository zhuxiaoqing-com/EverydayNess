package org.evd.game.OnlineService;

import org.evd.game.OnlineService.login.OnlineLoginLogic;
import org.evd.game.OnlineService.routing.OnlineRoutingLogic;
import org.evd.game.OnlineService.session.OnlineSessionLogic;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.support.LogCore;

import java.util.Collection;

public class OnlineService extends Service {
    /** 创建 OnlineService；业务状态由对应的 @Actor Logic 持有。 */
    public OnlineService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    /** 注册 token 清理、服务负载刷新和登录排队定时任务。 */
    @Override
    public void init() {
        super.init();
        newRepeatedTimerCoroutine(60_000L, false,
                () -> getActor(OnlineLoginLogic.class).cleanupExpiredTokens(getTimeCurrent()));
        newRepeatedTimerCoroutine(5_000L, true,
                () -> getActor(OnlineRoutingLogic.class).refresh());
        newRepeatedTimerCoroutine(1_000L, true,
                () -> getActor(OnlineLoginLogic.class).pumpAdmissionQueue());
        LogCore.core.info("OnlineService 初始化完成: service={}", id);
    }

    @Override
    public void tick() {
        super.tick();
        getActor(OnlineSessionLogic.class).tick(getTimeCurrent());
    }

    @Override
    protected void onServiceConnectReady(Collection<RegisteredService> serviceList) {
        super.onServiceConnectReady(serviceList);
        getActor(OnlineSessionLogic.class).onServiceConnectReady(serviceList);
    }

    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        super.onServiceDisconnect(serviceList);
        getActor(OnlineSessionLogic.class).onServiceDisconnect(serviceList);
    }

}
