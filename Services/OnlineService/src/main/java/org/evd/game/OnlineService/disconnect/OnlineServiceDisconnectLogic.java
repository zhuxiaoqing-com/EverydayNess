package org.evd.game.OnlineService.disconnect;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.login.OnlineLoginLogic;
import org.evd.game.OnlineService.offline.OnlineOfflineLogic;
import org.evd.game.OnlineService.routing.OnlineRoutingLogic;
import org.evd.game.OnlineService.session.OnlineSessionLogic;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.OnlineService.session.SOnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.Collection;

/** OnlineService 关联服务断开后的会话收敛逻辑。 */
@Actor
public final class OnlineServiceDisconnectLogic {
    /** 统一转发并处理 OnlineService 的关联服务断开事件。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        LogCore.core.info("OnlineService 开始处理关联服务断开: service={}, count={}", owner().getId(), serviceList.size());
        session().onServiceDisconnect(serviceList);
        routing().onServiceDisconnect(serviceList);
        login().onServiceDisconnect(serviceList);
        for (RegisteredService service : serviceList) {
            switch (service.getServiceType()) {
                case CONN -> onConnServiceDisconnect(service);
                case PLAYER -> onPlayerServiceDisconnect(service);
                default -> {
                }
            }
        }
        LogCore.core.info("OnlineService 完成关联服务断开: service={}, count={}", owner().getId(), serviceList.size());
    }

    /** ConnService 断开时，清理归属于该网关的在线会话。 */
    private void onConnServiceDisconnect(RegisteredService service) {
        int affected = 0;
        for (SOnlineUserState state : new ArrayList<>(session().getUserStates())) {
            if (!service.getCallPoint().equals(state.getActiveGate())) {
                continue;
            }
            LogCore.core.info("OnlineService ConnService 断开，处理玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    service.getCallPoint(), state.getUserId(), state.getActivePlayerId(),
                    state.getActiveGate(), state.getActiveGateSessionId());
            offline().offlineSession(state.getUserId(), state.getActiveGate(),
                    state.getActiveGateSessionId(), BrokenType.SERVER_KICK);
            affected++;
        }
        LogCore.core.info("OnlineService 完成 ConnService 断开清理: service={}, affected={}",
                service.getCallPoint(), affected);
    }

    /** PlayerService 断开时，先踢出网关会话，再清理 Online 会话。 */
    private void onPlayerServiceDisconnect(RegisteredService service) {
        int affected = 0;
        for (SOnlineUserState state : new ArrayList<>(session().getUserStates())) {
            if (!service.getCallPoint().equals(state.getActivePlayerService())) {
                continue;
            }
            LogCore.core.info("OnlineService PlayerService 断开，处理玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    service.getCallPoint(), state.getUserId(), state.getActivePlayerId(),
                    state.getActiveGate(), state.getActiveGateSessionId());
            offline().kickGateway(state.getUserId(), state.getActivePlayerId(),
                    state.getActiveGate(), state.getActiveGateSessionId(),
                    BrokenType.SERVER_KICK, "PlayerService 断开连接");
            offline().offlineSession(state.getUserId(), state.getActiveGate(),
                    state.getActiveGateSessionId(), BrokenType.SERVER_KICK);
            affected++;
        }
        LogCore.core.info("OnlineService 完成 PlayerService 断开清理: service={}, affected={}",
                service.getCallPoint(), affected);
    }

    private OnlineSessionLogic session() {
        return owner().getActor(OnlineSessionLogic.class);
    }

    private OnlineOfflineLogic offline() {
        return owner().getActor(OnlineOfflineLogic.class);
    }

    private OnlineRoutingLogic routing() {
        return owner().getActor(OnlineRoutingLogic.class);
    }

    private OnlineLoginLogic login() {
        return owner().getActor(OnlineLoginLogic.class);
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
