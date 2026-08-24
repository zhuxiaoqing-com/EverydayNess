package org.evd.game.OnlineService.login;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proto.C2S_Login2;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_Login2;
import org.evd.game.common.proxy.ConnService.ConnLoginActorProxy;
import org.evd.game.common.proxy.ConnService.ConnOfflineActorProxy;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.common.proxy.LobbyService.LobbyRoleActorProxy;
import org.evd.game.common.serializeBean.OnlineService.OnlineLoginAdmission;
import org.evd.game.common.serializeBean.OnlineService.OnlineTokenState;
import org.evd.game.common.serializeBean.OnlineService.OnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.support.LogCore;

@Actor
public final class OnlineLoginActor {
    /** 受理首段登录，按容量生成预登录状态或进入队列。 */
    @Rpc
    public OnlineLoginAdmission admitLogin(String userId, CallPoint requestGate, long requestSessionId) {
        return owner().loginCoordinator().admitLogin(
                userId, requestGate, requestSessionId, owner().getTimeCurrent());
    }

    /** 取消指定网关会话对应的排队登录请求。 */
    @Rpc
    public void cancelQueuedLogin(String userId, CallPoint requestGate, long requestSessionId) {
        owner().loginCoordinator().cancelQueuedLogin(userId, requestGate, requestSessionId);
    }

    /** 取消指定 token 对应的预登录状态。 */
    @Rpc
    public boolean cancelPendingSession(String userId, String token) {
        return owner().loginCoordinator().cancelPendingSession(userId, token);
    }

    /**
     * 处理客户端二段登录请求并完成登录状态提交。
     * 这个方法本身幂等;可以重复调用;
     * 所以不怕旧的协议重新发送该方法;而且也没法重复发;
     *  */
    @ClientCmd(MsgId.C2S_LOGIN2_VALUE)
    public void login2(ClientSessionRef session, C2S_Login2 req) {
        CallPoint gate = session.getGate();
        long gateSessionId = session.getSessionId();
        String userId = req.getUserId().trim();
        String token = req.getToken().trim();
        if (userId.isEmpty() || token.isEmpty()) {
            rejectLoginSession(gate, gateSessionId, userId, token, "userId 或 token 不能为空");
            return;
        }
        OnlineService owner = owner();
        login2Internal(owner, gate, gateSessionId, userId, token);
    }

    /** 校验预登录状态并执行二段登录流程。 */
    private void login2Internal(OnlineService owner, CallPoint gate, long gateSessionId,
                                String userId, String token) {
        OnlineTokenState tokenState = owner.loginCoordinator().getTokenState(userId, token);
        if (tokenState == null) {
            LogCore.core.info("OnlineService 二段登录拒绝: userId={}, gate={}, gateSessionId={}, reason=token invalid",
                    userId, gate, gateSessionId);
            rejectLoginSession(gate, gateSessionId, userId, token, "token 不存在或已被新的登录流程替换");
            return;
        }
        long expectedVersion = tokenState.getVersion();
        if (tokenState.getExpireAt() < owner.getTimeCurrent()) {
            LogCore.core.info("OnlineService 二段登录拒绝: userId={}, gateSessionId={}, version={}, reason=token expired",
                    userId, gateSessionId, expectedVersion);
            rejectLoginSession(gate, gateSessionId, userId, token, "token 已过期");
            return;
        }
        if (tokenState.getGate() == null || !tokenState.getGate().equals(gate)) {
            LogCore.core.warn("OnlineService 二段登录拒绝，Gate 不匹配: userId={}, gate={}, expectedGate={}, gateSessionId={}, version={}",
                    userId, gate, tokenState.getGate(), gateSessionId, expectedVersion);
            rejectLoginSession(gate, gateSessionId, userId, token, "token 对应 gate 不匹配");
            return;
        }
        if (!owner.loginCoordinator().renewPendingLogin(userId, token, expectedVersion,
                gate, owner.getTimeCurrent())) {
            LogCore.core.info("OnlineService 二段登录拒绝，预登录已变化: userId={}, gateSessionId={}, version={}",
                    userId, gateSessionId, expectedVersion);
            rejectLoginSession(gate, gateSessionId, userId, token, "token 已过期或已被新的登录流程替换");
            return;
        }

        OnlineTokenState currentToken = owner.loginCoordinator().getTokenState(userId, token);
        if (currentToken == null || currentToken.getVersion() != expectedVersion
                || currentToken.getGate() == null || !currentToken.getGate().equals(gate)) {
            LogCore.core.info("OnlineService 清理旧会话前预登录已变化: userId={}, gateSessionId={}, version={}",
                    userId, gateSessionId, expectedVersion);
            rejectLoginSession(gate, gateSessionId, userId, token, "token 已过期或已被新的登录流程替换");
            return;
        }

        OnlineUserState oldUserState = owner.sessionCoordinator().getUserState(userId);

        // 先清理旧会话的下游状态，但不在这里等待/踢旧 GW；旧 GW 要在新状态登记后再 call。
        CallPoint oldGate = oldUserState == null ? null : oldUserState.getActiveGate();
        long oldGateSessionId = oldUserState == null ? 0L : oldUserState.getActiveGateSessionId();
        owner.offlineCoordinator().offlineSession(userId, oldGate, oldGateSessionId,
                BrokenType.LOGIN_REPLACE);

        userLogin(owner, gate, gateSessionId, userId, token, expectedVersion, oldUserState);
    }

    /** 统一提交 ONLINE 用户、GW 登记，并在 GW 成功后返回玩家列表。 */
    private void userLogin(OnlineService owner, CallPoint gate, long gateSessionId,
                           String userId, String token, long version,
                           OnlineUserState oldUserState) {
        if (!owner.loginCoordinator().cancelPendingSession(userId, token)) {
            LogCore.core.warn("OnlineService 用户上线提交失败，删除预登录状态失败: userId={}, gate={}, gateSessionId={}, version={}",
                    userId, gate, gateSessionId, version);
            rejectLoginSession(gate, gateSessionId, userId, token, "登录状态提交失败");
            return;
        }

        owner.sessionCoordinator().createOnlineState(userId, gate, gateSessionId);

        kickOldGateway(oldUserState);

        OnlineUserState currentUserState = owner.sessionCoordinator().getUserState(userId);
        if (!owner.sessionCoordinator().matchesSession(userId, gate, gateSessionId)) {
            LogCore.core.warn("OnlineService 新建用户状态后会话已失效: userId={}, gate={}, gateSessionId={}, version={}, currentState={}",
                    userId, gate, gateSessionId, version, currentUserState);
            return;
        }

        RpcResult<Boolean> registerGateResult = ConnLoginActorProxy.callRegisterLogin(
                gate, gateSessionId, userId);
        if (!registerGateResult.isSuccess() || !Boolean.TRUE.equals(registerGateResult.getValue())) {
            LogCore.core.warn("OnlineService GW 用户登记失败: userId={}, gate={}, gateSessionId={}, version={}, errorCode={}, message={}, value={}",
                    userId, gate, gateSessionId, version,
                    registerGateResult.getErrorCode(), registerGateResult.getErrorMessage(),
                    registerGateResult.getValue());
            owner.offlineCoordinator().onSessionOffline(
                    userId, 0L, gate, gateSessionId, BrokenType.SERVER_KICK.getCode());
            closeLoginGateway(gate, gateSessionId, "新 GW 用户登记失败");
            return;
        }

        if (!owner.sessionCoordinator().matchesSession(userId, gate, gateSessionId)) {
            closeLoginGateway(gate, gateSessionId, "新 GW 登录状态已被替换");
            return;
        }

        CallPoint lobbyRemote = owner.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        if (lobbyRemote == null) {
            LogCore.core.warn("OnlineService 用户上线后 LobbyService 不可用: userId={}, gateSessionId={}, version={}",
                    userId, gateSessionId, version);
            redirectLoginFailure(gate, gateSessionId);
            return;
        }

        RpcResult<Void> roleListResult = LobbyRoleActorProxy.sendRoleList(
                lobbyRemote, gate, gateSessionId, userId);
        if (!roleListResult.isSuccess()) {
            LogCore.core.warn("OnlineService 请求 LobbyService 发送角色列表失败: userId={}, gateSessionId={}, version={}, errorCode={}, message={}",
                    userId, gateSessionId, version,
                    roleListResult.getErrorCode(), roleListResult.getErrorMessage());
            redirectLoginFailure(gate, gateSessionId);
            return;
        }
        LogCore.core.info("OnlineService 二段登录成功: userId={}, gate={}, gateSessionId={}, version={}",
                userId, gate, gateSessionId, version);
    }

    /** 新会话登记后再同步关闭旧 GW；旧 GW 的延迟离线通知会被当前会话校验忽略。 */
    private void kickOldGateway(OnlineUserState oldUserState) {
        if (oldUserState == null || oldUserState.getActiveGate() == null
                || oldUserState.getActiveGateSessionId() <= 0L) {
            return;
        }
        RpcResult<Void> result = ConnOfflineActorProxy.sendCloseSession(
                oldUserState.getActiveGate(), oldUserState.getActiveGateSessionId(),
                BrokenType.LOGIN_REPLACE.getCode(), "duplicate login");
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 踢旧 GW 失败: gate={}, gateSessionId={}, errorCode={}, message={}, value={}",
                    oldUserState.getActiveGate(), oldUserState.getActiveGateSessionId(),
                    result.getErrorCode(), result.getErrorMessage(), result.getValue());
        }
    }

    /** 登录提交期间发现新 GW 已不是当前会话时，关闭本次 GW 连接。 */
    private void closeLoginGateway(CallPoint gate, long gateSessionId, String reason) {
        RpcResult<Void> result = ConnOfflineActorProxy.sendCloseSession(
                gate, gateSessionId, BrokenType.SERVER_KICK.getCode(), reason);
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 关闭失效新 GW 失败: gate={}, gateSessionId={}, reason={}, errorCode={}, message={}, value={}",
                    gate, gateSessionId, reason,
                    result.getErrorCode(), result.getErrorMessage(), result.getValue());
        }
    }

    /** 向待登录连接发送失败响应并拒绝其预登录会话。 */
    private void rejectLoginSession(CallPoint gate, long gateSessionId,
                                    String userId, String token, String reason) {
        S2C_Login2 response = S2C_Login2.newBuilder()
                .setSuccess(false)
                .setMessage(reason)
                .build();
        RpcResult<Boolean> result = ConnLoginActorProxy.callRejectPendingLogin(
                gate, gateSessionId, userId, token,
                ClientFrameChunk.wrap(MsgId.S2C_LOGIN2_VALUE, response),
                BrokenType.TOKEN_EXPIRE.getCode(), reason);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            LogCore.core.warn("OnlineService 拒绝二段登录连接失败: userId={}, gate={}, gateSessionId={}, reason={}, errorCode={}, message={}, value={}",
                    userId, gate, gateSessionId, reason,
                    result.getErrorCode(), result.getErrorMessage(), result.getValue());
        }
    }

    /** 向登录失败的连接发送失败响应。 */
    private void redirectLoginFailure(CallPoint gate, long gateSessionId) {
        String reason = "LobbyService 不可用";
        S2C_Login2 response = S2C_Login2.newBuilder()
                .setSuccess(false)
                .setMessage(reason)
                .build();
        RpcResult<Void> result = ConnServiceProxy.sendRedirectClient(
                gate, gateSessionId, ClientFrameChunk.wrap(MsgId.S2C_LOGIN2_VALUE, response));
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 回登录失败并关闭连接时发送失败: gate={}, gateSessionId={}, reason={}, errorCode={}, message={}",
                    gate, gateSessionId, reason, result.getErrorCode(), result.getErrorMessage());
        }
    }

    /** 获取当前执行上下文中的 OnlineService 实例。 */
    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }

}
