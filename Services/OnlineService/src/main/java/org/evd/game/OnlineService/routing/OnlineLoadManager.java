package org.evd.game.OnlineService.routing;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.common.serializeBean.OnlineService.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.OnlinePlayerCandidate;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.RegisteredService;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

/** OnlineService 统一维护 ConnService、PlayerService 的负载快照。 */
public final class OnlineLoadManager {
    private record ConnLoad(String publicAddr, int loginCount) {
    }

    private final OnlineService owner;
    private final Map<CallPoint, ConnLoad> connLoads = new HashMap<>();
    private final Map<CallPoint, Integer> playerLoads = new HashMap<>();

    /** 创建 ConnService、PlayerService 负载快照管理器。 */
    public OnlineLoadManager(OnlineService owner) {
        this.owner = owner;
    }

    /** 重新读取所有 ConnService 和 PlayerService 的当前负载。 */
    public void refresh() {
        refreshConnLoads();
        refreshPlayerLoads();
    }

    /** 从缓存中选择登录数最少的 ConnService。 */
    public OnlineConnCandidate selectLeastLoadedConn() {
        if (connLoads.isEmpty()) {
            refreshConnLoads();
        }
        OnlineConnCandidate best = null;
        for (Map.Entry<CallPoint, ConnLoad> entry : connLoads.entrySet()) {
            ConnLoad load = entry.getValue();
            if (best == null || load.loginCount() < best.getLoginCount()) {
                best = new OnlineConnCandidate(entry.getKey(), load.publicAddr(), load.loginCount());
            }
        }
        if (best == null) {
            LogCore.core.warn("OnlineService 找不到可用 ConnService: service={}", owner.getId());
        }
        return best;
    }

    /** 从缓存中选择在线玩家数最少的 PlayerService。 */
    public OnlinePlayerCandidate selectLeastLoadedPlayer() {
        if (playerLoads.isEmpty()) {
            refreshPlayerLoads();
        }
        OnlinePlayerCandidate best = null;
        for (Map.Entry<CallPoint, Integer> entry : playerLoads.entrySet()) {
            if (best == null || entry.getValue() < best.getOnlineCount()) {
                best = new OnlinePlayerCandidate(entry.getKey(), entry.getValue());
            }
        }
        if (best == null) {
            LogCore.core.warn("OnlineService 找不到可用 PlayerService: service={}", owner.getId());
        }
        return best;
    }

    /** 优先复用用户历史 PlayerService；历史服务当前不可用时再选择负载最低的服务。 */
    public OnlinePlayerCandidate selectLeastLoadedPlayer(String userId) {
        CallPoint historicalPlayerService = owner.sessionCoordinator()
                .getHistoricalPlayerService(userId);
        RegisteredService offlineService = owner.getNode().getOfflineService(historicalPlayerService);
        if (offlineService != null) {
            // 怕PlayerService刚好离线，数据还没同步到数据库，这个时候切换，可能会导致脏数据
            LogCore.core.warn("OnlineService 历史 PlayerService 离线CD中，暂不选择该服务: userId={}, playerService={}, offlineMill={}",
                    userId, historicalPlayerService, offlineService.getOfflineMill());
            return null;
        }

        if (playerLoads.isEmpty()) {
            refreshPlayerLoads();
        }
        if (historicalPlayerService != null) {
            Integer onlineCount = playerLoads.get(historicalPlayerService);
            if (onlineCount != null) {
                LogCore.core.info("OnlineService 优先复用历史 PlayerService: playerService={}, onlineCount={}",
                        historicalPlayerService, onlineCount);
                return new OnlinePlayerCandidate(historicalPlayerService, onlineCount);
            }
            LogCore.core.warn("OnlineService 历史 PlayerService 当前不可用，改用负载选择: playerService={}",
                    historicalPlayerService);
        }
        return selectLeastLoadedPlayer();
    }

    /** 查询并缓存所有 ConnService 的公网地址和登录数。 */
    private void refreshConnLoads() {
        Map<CallPoint, ConnLoad> latest = new HashMap<>();
        for (RegisteredService service : owner.getNode().getServicesByType(ServiceType.CONN)) {
            CallPoint callPoint = service.getCallPoint();
            RpcResult<String> publicAddrResult = ConnServiceProxy.callGetPublicAddr(callPoint);
            RpcResult<Integer> loginCountResult = ConnServiceProxy.callGetLoginSessionCount(callPoint);
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
        connLoads.clear();
        connLoads.putAll(latest);
    }

    /** 查询并缓存所有 PlayerService 的在线玩家数。 */
    private void refreshPlayerLoads() {
        Map<CallPoint, Integer> latest = new HashMap<>();
        for (RegisteredService service : owner.getNode().getServicesByType(ServiceType.PLAYER)) {
            CallPoint callPoint = service.getCallPoint();
            RpcResult<Integer> onlineCountResult = PlayerServiceProxy.callGetOnlineCount(callPoint);
            if (!onlineCountResult.isSuccess()) {
                LogCore.core.warn("OnlineService 刷新 PlayerService 负载失败: callPoint={}, errorCode={}, message={}",
                        callPoint, onlineCountResult.getErrorCode(), onlineCountResult.getErrorMessage());
                continue;
            }
            latest.put(callPoint, onlineCountResult.getValue());
        }
        playerLoads.clear();
        playerLoads.putAll(latest);
    }
}
