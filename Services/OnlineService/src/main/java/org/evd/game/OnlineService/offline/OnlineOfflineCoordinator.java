package org.evd.game.OnlineService.offline;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.common.proxy.ConnService.ConnOfflineRpcProxy;
import org.evd.game.common.proxy.PlayerService.PlayerOfflineRpcProxy;
import org.evd.game.common.serializeBean.OnlineService.session.OnlineUserState;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

/** OnlineService 离线流程的唯一实现，负责下游通知和本地在线状态清理。 */
public final class OnlineOfflineCoordinator {
    private final OnlineService owner;

    public OnlineOfflineCoordinator(OnlineService owner) {
        this.owner = owner;
    }

    /** 统一向当前 GW 发送带 sessionId 的踢下线命令。 */
    public void kickGateway(CallPoint gate, long gateSessionId,
                            BrokenType brokenType, String reason) {
        RpcResult<Void> result = ConnOfflineRpcProxy.sendKickSession(
                gate, gateSessionId, brokenType.getCode(), reason);
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 踢出 GW 失败: gate={}, sessionId={}, brokenType={}, errorCode={}, message={}",
                    gate, gateSessionId, brokenType, result.getErrorCode(), result.getErrorMessage());
        }
    }

    /** 处理网关下线通知，并释放当前用户和玩家状态。 */
    public void onSessionOffline(String userId, long playerId, CallPoint gate, long gateSessionId,
                                 int brokenTypeCode) {
        OnlineUserState userState = owner.sessionCoordinator().getUserState(userId);
        if (userState != null && playerId > 0L && playerId != userState.getActivePlayerId()) {
            LogCore.core.warn("OnlineService 离线通知 playerId 与当前状态不一致，按当前状态清理: userId={}, notifiedPlayerId={}, currentPlayerId={}",
                    userId, playerId, userState.getActivePlayerId());
        }
        offlineSession(userId, gate, gateSessionId, BrokenType.fromCode(brokenTypeCode));
    }

    /** 按网关和会话号校验后执行通用离线流程。 */
    public void offlineSession(String userId, CallPoint gate,
                               long gateSessionId, BrokenType brokenType) {
        OnlineSessionCoordinator session = owner.sessionCoordinator();
        OnlineUserState userState = session.getUserState(userId);
        if (isSessionMismatch(userState, gate, gateSessionId)) {
            LogCore.core.info("OnlineService 忽略旧 Session 下线: userId={}, gate={}, gateSessionId={}, current={}",
                    userId, gate, gateSessionId, userState);
            return;
        }

        CallPoint playerService = userState.getActivePlayerService();
        long actualPlayerId = userState.getActivePlayerId();
        if (playerService == null || actualPlayerId <= 0L) {
            LogCore.core.info("OnlineService 处理无玩家绑定的离线: userId={}, gateSessionId={}, brokenType={}",
                    userState.getUserId(), gateSessionId, brokenType);
        } else {
            RpcResult<Void> result = PlayerOfflineRpcProxy.sendOnPlayerOffline(
                    playerService, userState.getUserId(), actualPlayerId,
                    userState.getActiveGate(), userState.getActiveGateSessionId(),
                    brokenType.getCode());
            if (!result.isSuccess()) {
                LogCore.core.error("OnlineService 发送 PlayerService 离线通知失败，但继续完成本地离线: userId={}, playerId={}, gateSessionId={}, playerService={}, errorCode={}, message={}",
                        userState.getUserId(), actualPlayerId, userState.getActiveGateSessionId(),
                        playerService, result.getErrorCode(), result.getErrorMessage());
            }
        }

        LogCore.core.info("OnlineService 处理离线: userId={}, playerId={}, gate={}, gateSessionId={}, brokenType={}",
                userState.getUserId(), actualPlayerId, userState.getActiveGate(),
                userState.getActiveGateSessionId(), brokenType);
        session.removeOnlinePlayer(userId, gate, gateSessionId);
        session.removeOnlineState(userId);
    }

    private boolean isSessionMismatch(OnlineUserState state, CallPoint gate, long gateSessionId) {
        return state == null || gate == null || !gate.equals(state.getActiveGate())
                || gateSessionId != state.getActiveGateSessionId();
    }
}
