package org.evd.game.OnlineService;

import org.evd.game.OnlineService.login.OnlineLoginCoordinator;
import org.evd.game.OnlineService.offline.OnlineOfflineCoordinator;
import org.evd.game.OnlineService.routing.OnlineServiceSelector;
import org.evd.game.OnlineService.reconcile.OnlineStateReconcileManager;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.support.LogCore;

public class OnlineService extends Service {
    private final OnlineServiceSelector serviceSelector;
    private final OnlineSessionCoordinator sessionCoordinator;
    private final OnlineLoginCoordinator loginCoordinator;
    private final OnlineOfflineCoordinator offlineCoordinator;
    private final OnlineStateReconcileManager stateReconcileManager;

    /** 创建 OnlineService，并初始化负载选择、登录准入和会话状态协调器。 */
    public OnlineService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.serviceSelector = new OnlineServiceSelector(this);
        this.sessionCoordinator = new OnlineSessionCoordinator();
        this.offlineCoordinator = new OnlineOfflineCoordinator(this);
        this.stateReconcileManager = new OnlineStateReconcileManager(
                sessionCoordinator, offlineCoordinator);
        this.loginCoordinator = new OnlineLoginCoordinator(
                this, serviceSelector, sessionCoordinator, GlobalYml.requireNodeConfig().getLogin());
    }

    /** 注册 token 清理、服务负载刷新和登录排队定时任务。 */
    @Override
    public void init() {
        super.init();
        newRepeatedTimer(60_000L, false, this::cleanupExpiredTokens);
        newRepeatedTimer(5_000L, true, serviceSelector::refresh);
        newRepeatedTimer(1_000L, true, loginCoordinator::pumpAdmissionQueue);
        LogCore.core.info("OnlineService 初始化完成: service={}", id);
    }

    /** 推进在线会话映射的过期清理。 */
    @Override
    public void tick() {
        super.tick();
        sessionCoordinator.tick(getTimeCurrent());
    }

    /** 返回 OnlineService 的网关和 PlayerService 负载选择器。 */
    public OnlineServiceSelector serviceSelector() {
        return serviceSelector;
    }

    /** 返回正式在线状态协调器。 */
    public OnlineSessionCoordinator sessionCoordinator() {
        return sessionCoordinator;
    }

    /** 返回登录准入、token 和排队处理器。 */
    public OnlineLoginCoordinator loginCoordinator() {
        return loginCoordinator;
    }

    /** 返回离线流程协调器，登录替换旧会话也通过此入口清理旧状态。 */
    public OnlineOfflineCoordinator offlineCoordinator() {
        return offlineCoordinator;
    }

    /** 返回状态对账校验器，供 OnlineStateReconcileRpc 委托处理。 */
    public OnlineStateReconcileManager stateReconcileManager() {
        return stateReconcileManager;
    }

    /** 清理已经超过有效期的预登录 token。 */
    private void cleanupExpiredTokens() {
        loginCoordinator.cleanupExpiredTokens(getTimeCurrent());
    }
}
