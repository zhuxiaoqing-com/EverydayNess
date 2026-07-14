package org.evd.game.LobbyService.login;

import org.evd.game.LobbyService.LobbyRoleActor;
import org.evd.game.LobbyService.LobbyService;
import org.evd.game.LobbyService.routing.LobbyConnCandidate;
import org.evd.game.LobbyService.routing.LobbyLoadBalancerActor;
import org.evd.game.LobbyService.session.LobbySessionRepository;
import org.evd.game.LobbyService.session.LobbyTokenState;
import org.evd.game.LobbyService.session.LobbyUserState;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proto.*;
import org.evd.game.common.proxy.SdkService.SdkServiceProxy;
import org.evd.game.common.serializeBean.SdkService.SdkValidateResult;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.List;
import java.util.UUID;

@Actor
public final class LobbyLoginActor {
    private static final long TOKEN_TTL_MILLIS = 5 * 60 * 1000L;
   // private static final long SDK_VALIDATE_TIMEOUT_MILLIS = 5_000L;

    @ClientCmd(MsgId.C2S_LOGIN_VALUE)
    public void login(ClientSessionRef session, C2S_Login req) {
        LobbyService owner = owner();
        LobbyLoadBalancerActor loadBalancerActor = owner.loadBalancerActor();
        LobbySessionRepository sessionRepository = owner.sessionRepository();
        String userId = req.getUserId().trim();
        if (userId.isEmpty()) {
            pushLoginResp(session, false, "userId 不能为空", "", "", 0L);
            return;
        }

        CallPoint sdkRemote = owner.getNode().getAnyCallPointByType(ServiceType.SDK);
        if (sdkRemote == null) {
            pushLoginResp(session, false, "SdkService 未配置", "", "", 0L);
            return;
        }

        RpcResult<SdkValidateResult> validateRpcResult = SdkServiceProxy
                .callRequestValidate(sdkRemote, userId, req.getSdkToken());
        if (!validateRpcResult.isSuccess()) {
            LogCore.core.warn("LobbyService SDK 校验 RPC 失败: userId={}, errorCode={}, message={}",
                    userId, validateRpcResult.getErrorCode(), validateRpcResult.getErrorMessage());
            pushLoginResp(session, false, "SDK 服务不可用", "", "", 0L);
            return;
        }
        SdkValidateResult validateResult = validateRpcResult.getValue();
        if (!validateResult.isSuccess()) {
            pushLoginResp(session, false, validateResult.getMessage(), "", "", 0L);
            return;
        }

        LobbyConnCandidate targetGate = loadBalancerActor.selectLeastLoadedConn();
        if (targetGate == null) {
            pushLoginResp(session, false, "找不到可用 gate", "", "", 0L);
            return;
        }

        LobbyUserState userState = sessionRepository.getOrCreateUser(userId);
        sessionRepository.invalidatePendingToken(userState);

        String token = UUID.randomUUID().toString();
        long expireAt = owner.getTimeCurrent() + TOKEN_TTL_MILLIS;
        sessionRepository.saveToken(token, new LobbyTokenState(userId, targetGate.callPoint(), expireAt));
        userState.setPendingToken(token);

        pushLoginResp(session, true, "ok", targetGate.publicAddr(), token, expireAt);
    }

    @ClientCmd(MsgId.C2S_LOGIN2_VALUE)
    public void login2(ClientSessionRef session, C2S_Login2 req) {
        LobbyService owner = owner();
        LobbySessionRepository sessionRepository = owner.sessionRepository();
        String token = req.getToken().trim();
        LobbyTokenState tokenState = sessionRepository.findToken(token);
        if (tokenState == null) {
            pushLogin2Resp(session, false, "token 不存在或已失效", List.of());
            return;
        }
        if (tokenState.getExpireAt() < owner.getTimeCurrent()) {
            sessionRepository.removeToken(token);
            pushLogin2Resp(session, false, "token 已过期", List.of());
            return;
        }
        if (tokenState.getGateCallPoint() == null || !tokenState.getGateCallPoint().equals(session.getGate())) {
            pushLogin2Resp(session, false, "token 对应 gate 不匹配", List.of());
            return;
        }

        LobbyUserState userState = sessionRepository.getOrCreateUser(tokenState.getUserId());
        var oldGate = userState.getActiveGate();
        long oldSessionId = userState.getActiveSessionId();
        if (oldGate != null && (!oldGate.equals(session.getGate()) || oldSessionId != session.getSessionId())) {
            RpcResult<Boolean> kickResult = ConnServiceProxy.callKickSession(
                    oldGate,
                    oldSessionId,
                    BrokenType.LOGIN_REPLACE.getCode(),
                    "duplicate login"
            );
            if (!kickResult.isSuccess() || !Boolean.TRUE.equals(kickResult.getValue())) {
                LogCore.core.warn("LobbyService 踢出旧会话失败: sessionId={}, errorCode={}, message={}",
                        oldSessionId, kickResult.getErrorCode(), kickResult.getErrorMessage());
                pushLogin2Resp(session, false, "旧会话关闭失败", List.of());
                return;
            }
            sessionRepository.clearActiveSession(userState);
        }

        RpcResult<Boolean> confirmResult = ConnServiceProxy.callConfirmLogin(
                session.getGate(),
                session.getSessionId(),
                tokenState.getUserId(),
                0L
        );
        if (!confirmResult.isSuccess()) {
            LogCore.core.warn("LobbyService 确认 gate 登录失败: userId={}, sessionId={}, errorCode={}, message={}",
                    tokenState.getUserId(), session.getSessionId(),
                    confirmResult.getErrorCode(), confirmResult.getErrorMessage());
            pushLogin2Resp(session, false, "gate 服务不可用", List.of());
            return;
        }
        if (!Boolean.TRUE.equals(confirmResult.getValue())) {
            pushLogin2Resp(session, false, "gate 登录确认失败", List.of());
            return;
        }

        sessionRepository.bindActiveSession(userState, session.getGate(), session.getSessionId());
        sessionRepository.removeToken(token);
        if (token.equals(userState.getPendingToken())) {
            userState.clearPendingToken();
        }

        pushLogin2Resp(session, true, "ok", LobbyRoleActor.buildRoleList(userState));
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }


    public void pushLoginResp(
            ClientSessionRef session,
            boolean success,
            String message,
            String gateAddr,
            String token,
            long expireAt
    ) {
        S2C_Login resp = S2C_Login.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .setGateAddr(gateAddr == null ? "" : gateAddr)
                .setToken(token == null ? "" : token)
                .setTokenExpireAt(expireAt)
                .build();
        RpcResult<Void> pushResult = ConnServiceProxy.callPushToClient(session.getGate(), session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_LOGIN_VALUE, resp));
        if (!pushResult.isSuccess()) {
            LogCore.core.warn("LobbyService 回登录响应失败: sessionId={}, errorCode={}, message={}",
                    session.getSessionId(), pushResult.getErrorCode(), pushResult.getErrorMessage());
        }
    }

    public void pushLogin2Resp(ClientSessionRef session, boolean success, String message, List<RoleData> roles) {
        S2C_Login2.Builder builder = S2C_Login2.newBuilder()
                .setSuccess(success)
                .setMessage(message);
        builder.addAllRoles(roles);
        RpcResult<Void> pushResult = ConnServiceProxy.callPushToClient(session.getGate(), session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_LOGIN2_VALUE, builder.build()));
        if (!pushResult.isSuccess()) {
            LogCore.core.warn("LobbyService 回登录确认响应失败: sessionId={}, errorCode={}, message={}",
                    session.getSessionId(), pushResult.getErrorCode(), pushResult.getErrorMessage());
        }
    }


}
