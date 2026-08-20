package org.evd.game.OnlineService.logout;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.common.serializeBean.OnlineService.OnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

/** OnlineService 的登出和断线通知 RPC 入口。 */
@Actor
public final class OnlineLogoutActor {
    /** 处理网关下线通知，并释放当前用户和玩家状态。 */
    @Rpc
    public void onSessionOffline(String userId, long playerId, CallPoint gate, long gateSessionId,
                                 int brokenTypeCode) {
        offlineSession(userId, gate, gateSessionId,
                BrokenType.fromCode(brokenTypeCode));
    }

    /** 按 GW 网关和会话号校验后执行通用下线流程。 */
    public boolean offlineSession(String userId, CallPoint gate,
                                  long gateSessionId, BrokenType brokenType) {
        OnlineService owner = owner();
        OnlineSessionCoordinator session = owner.sessionCoordinator();
        OnlineUserState userState = session.getUserState(userId);
        if (isSessionMismatch(userState, gate, gateSessionId)) {
            LogCore.core.info("OnlineService 忽略旧 Session 下线: userId={}, gate={}, gateSessionId={}, current={}",
                    userId, gate, gateSessionId, userState);
            return true;
        }

        CallPoint playerService = userState.getActivePlayerService();
        long actualPlayerId = userState.getActivePlayerId();
        if (playerService == null || actualPlayerId <= 0L) {
            LogCore.core.info("OnlineService 处理无玩家绑定的离线: userId={}, gateSessionId={}, brokenType={}",
                    userState.getUserId(), gateSessionId, brokenType);
        } else {
            RpcResult<Void> result = PlayerServiceProxy.sendOnPlayerOffline(
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
        return true;
    }

    private boolean isSessionMismatch(OnlineUserState state, CallPoint gate, long gateSessionId) {
        return state == null || gate == null || !gate.equals(state.getActiveGate())
                || gateSessionId != state.getActiveGateSessionId();
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
