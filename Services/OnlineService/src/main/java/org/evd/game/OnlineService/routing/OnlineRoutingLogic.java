package org.evd.game.OnlineService.routing;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.session.OnlineSessionLogic;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.PlayerService.PlayerServiceRpcProxy;
import org.evd.game.common.serializeBean.OnlineService.routing.SOnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.routing.SOnlinePlayerCandidate;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/** OnlineService 负载选择业务逻辑。 */
@Actor
public final class OnlineRoutingLogic {
    private record ConnLoad(String publicAddr, int loginCount) {
    }

    private final Map<CallPoint, ConnLoad> connLoads = new HashMap<>();
    private final Map<CallPoint, Integer> playerLoads = new HashMap<>();

    /** 立即移除离线服务，避免下一次定时刷新前仍被新登录选中。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        int removed = 0;
        for (RegisteredService service : serviceList) {
            if (connLoads.remove(service.getCallPoint()) != null) {
                removed++;
            }
            if (playerLoads.remove(service.getCallPoint()) != null) {
                removed++;
            }
        }
        if (removed > 0) {
            LogCore.core.info("OnlineService 清理断开服务负载候选: removed={}", removed);
        }
    }

    /** 返回当前负载最低的 ConnService。 */
    public SOnlineConnCandidate selectLeastLoadedConn() {
        if (connLoads.isEmpty()) {
            refreshConnLoads();
        }
        SOnlineConnCandidate best = null;
        for (Map.Entry<CallPoint, ConnLoad> entry : connLoads.entrySet()) {
            ConnLoad load = entry.getValue();
            if (best == null || load.loginCount() < best.getLoginCount()) {
                best = new SOnlineConnCandidate(entry.getKey(), load.publicAddr(), load.loginCount());
            }
        }
        if (best == null) {
            LogCore.core.warn("OnlineService 找不到可用 ConnService: service={}", owner().getId());
        }
        return best;
    }

    /** 返回当前负载最低的 PlayerService。 */
    private SOnlinePlayerCandidate selectLeastLoadedPlayer() {
        if (playerLoads.isEmpty()) {
            LogCore.core.warn("OnlineService 没有可用 PlayerService 负载: service={}", owner().getId());
            return null;
        }
        SOnlinePlayerCandidate best = null;
        for (Map.Entry<CallPoint, Integer> entry : playerLoads.entrySet()) {
            if (best == null || entry.getValue() < best.getOnlineCount()) {
                best = new SOnlinePlayerCandidate(entry.getKey(), entry.getValue());
            }
        }
        return best;
    }

    /** 优先复用用户历史 PlayerService；历史服务不可用时再选择负载最低的服务。 */
    public SOnlinePlayerCandidate selectLeastLoadedPlayer(String userId) {
        CallPoint historicalPlayerService = session().getHistoricalPlayerService(userId);
        if (playerLoads.isEmpty()) {
            LogCore.core.warn("OnlineService 没有可用 PlayerService 负载，无法为用户选择服务: userId={}, service={}",
                    userId, owner().getId());
            return null;
        }
        if (historicalPlayerService != null) {
            Integer onlineCount = playerLoads.get(historicalPlayerService);
            if(onlineCount == null) {
                LogCore.core.warn("OnlineService 历史 PlayerService 当前不可用: userId={}, playerService={}",
                        userId, historicalPlayerService);
                return null;
            }
            LogCore.core.info("OnlineService 优先复用历史 PlayerService: playerService={}, onlineCount={}",
                    historicalPlayerService, onlineCount);
            return new SOnlinePlayerCandidate(historicalPlayerService, onlineCount);
        }

        return selectLeastLoadedPlayer();
    }

    /** 刷新 ConnService 和 PlayerService 的负载快照。 */
    public void refresh() {
        refreshConnLoads();
        refreshPlayerLoads();
    }

    private void refreshConnLoads() {
        Map<CallPoint, ConnLoad> latest = new HashMap<>();
        for (RegisteredService service : owner().getServicesByType(ServiceType.CONN)) {
            CallPoint callPoint = service.getCallPoint();
            RpcResult<String> publicAddrResult = ConnServiceRpcProxy.callGetPublicAddr(callPoint);
            RpcResult<Integer> loginCountResult = ConnServiceRpcProxy.callGetLoginSessionCount(callPoint);
            if (!publicAddrResult.isSuccess() || !loginCountResult.isSuccess()) {
                LogCore.core.warn("OnlineService 刷新 ConnService 负载失败: callPoint={}, publicAddrError={}, loginCountError={}",
                        callPoint, publicAddrResult.getErrorMessage(), loginCountResult.getErrorMessage());
                continue;
            }
            String publicAddr = publicAddrResult.getValue();
            if (publicAddr == null || publicAddr.isBlank()) {
                LogCore.core.warn("OnlineService 忽略未配置公网地址的 ConnService: callPoint={}", callPoint);
                continue;
            }
            latest.put(callPoint, new ConnLoad(publicAddr, loginCountResult.getValue()));
        }
        // 拉取负载期间会挂起协程，不能把期间断开的服务重新写回候选。
        latest.keySet().retainAll(owner().getCallPointByType(ServiceType.CONN));
        connLoads.clear();
        connLoads.putAll(latest);
    }

    private void refreshPlayerLoads() {
        Map<CallPoint, Integer> latest = new HashMap<>();
        for (RegisteredService service : owner().getServicesByType(ServiceType.PLAYER)) {
            CallPoint callPoint = service.getCallPoint();
            RpcResult<Integer> onlineCountResult = PlayerServiceRpcProxy.callGetOnlineCount(callPoint);
            if (!onlineCountResult.isSuccess()) {
                LogCore.core.warn("OnlineService 刷新 PlayerService 负载失败: callPoint={}, errorCode={}, message={}",
                        callPoint, onlineCountResult.getErrorCode(), onlineCountResult.getErrorMessage());
                continue;
            }
            latest.put(callPoint, onlineCountResult.getValue());
        }
        latest.keySet().retainAll(owner().getCallPointByType(ServiceType.PLAYER));
        playerLoads.clear();
        playerLoads.putAll(latest);
    }

    private OnlineSessionLogic session() {
        return owner().getActor(OnlineSessionLogic.class);
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
