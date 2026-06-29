package org.evd.game.LobbyService.login;

import org.evd.game.LobbyService.LobbyRoleActor;
import org.evd.game.LobbyService.LobbyService;
import org.evd.game.LobbyService.routing.LobbyConnCandidate;
import org.evd.game.LobbyService.routing.LobbyLoadBalancerActor;
import org.evd.game.LobbyService.session.LobbySessionRepository;
import org.evd.game.LobbyService.session.LobbyTokenState;
import org.evd.game.LobbyService.session.LobbyUserState;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proto.*;
import org.evd.game.common.proxy.SdkService.SdkServiceProxy;
import org.evd.game.common.sdk.SdkValidateResult;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;

import java.util.List;
import java.util.UUID;

public final class LobbyLoginActor {
    private static final long TOKEN_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long SDK_VALIDATE_TIMEOUT_MILLIS = 5_000L;

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

        SdkValidateResult validateResult = SdkServiceProxy.inst()
                .requestValidate(sdkRemote, userId, req.getSdkToken(), SDK_VALIDATE_TIMEOUT_MILLIS);
        if (!validateResult.success()) {
            pushLoginResp(session, false, validateResult.message(), "", "", 0L);
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
        boolean confirm = ConnServiceProxy.inst().confirmLogin(
                session.getGate(),
                session.getSessionId(),
                tokenState.getUserId(),
                0L
        );
        if (!confirm) {
            pushLogin2Resp(session, false, "gate 登录确认失败", List.of());
            return;
        }

        var oldGate = userState.getActiveGate();
        long oldSessionId = userState.getActiveSessionId();
        sessionRepository.bindActiveSession(userState, session.getGate(), session.getSessionId());
        sessionRepository.removeToken(token);
        if (token.equals(userState.getPendingToken())) {
            userState.clearPendingToken();
        }

        if (oldGate != null && (!oldGate.equals(session.getGate()) || oldSessionId != session.getSessionId())) {
            ConnServiceProxy.inst().kickSession(
                    oldGate,
                    oldSessionId,
                    BrokenType.LOGIN_REPLACE.getCode(),
                    "duplicate login"
            );
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
        ConnServiceProxy.inst().pushToClient(session.getGate(), session.getSessionId(), MsgId.S2C_LOGIN_VALUE, new Chunk(resp));
    }

    public void pushLogin2Resp(ClientSessionRef session, boolean success, String message, List<RoleData> roles) {
        S2C_Login2.Builder builder = S2C_Login2.newBuilder()
                .setSuccess(success)
                .setMessage(message);
        builder.addAllRoles(roles);
        ConnServiceProxy.inst().pushToClient(session.getGate(), session.getSessionId(), MsgId.S2C_LOGIN2_VALUE, new Chunk(builder.build()));
    }


}
