package org.evd.game.ConnService.login;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proto.C2S_Login;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_Login;
import org.evd.game.common.proxy.LobbyService.LobbyServiceRpcProxy;
import org.evd.game.common.proxy.OnlineService.OnlineLoginRpcProxy;
import org.evd.game.common.proxy.SdkService.SdkServiceRpcProxy;
import org.evd.game.common.serializeBean.LobbyService.login.LobbyUserAccessResult;
import org.evd.game.common.serializeBean.OnlineService.login.OnlineLoginAdmission;
import org.evd.game.common.serializeBean.OnlineService.login.OnlineTokenState;
import org.evd.game.common.serializeBean.SdkService.login.SdkValidateResult;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.support.LogCore;

/** ConnService 首段客户端登录入口。 */
@Actor
public final class ConnLoginLogic {
    public boolean registerLogin(long sessionId, String userId) {
        return owner().loginManager().registerLogin(sessionId, userId);
    }

    public ActorAddress bindPlayer(long sessionId, long playerId, ActorAddress playerActorAddress) {
        return owner().loginManager().bindPlayer(sessionId, playerId, playerActorAddress);
    }

    public boolean rejectPendingLogin(long sessionId, String userId, String token,
                                      ClientFrameChunk packet, int brokenTypeCode, String reason) {
        return owner().loginManager().rejectPendingLogin(
                owner().findClientChannel(sessionId), sessionId, userId, token, packet,
                BrokenType.fromCode(brokenTypeCode), reason);
    }

    public void login(ClientSessionRef session, C2S_Login request) {
        ConnService owner = owner();
        String userId = request.getUserId().trim();
        if (userId.isEmpty()) {
            reject(owner, session, userId, "userId 不能为空", false);
            return;
        }
        LogCore.core.info("ConnService 收到首段登录: service={}, sessionId={}, userId={} ",
                owner.getId(), session.getSessionId(), userId);
        CallPoint sdkRemote = owner.getNode().getAnyCallPointByType(ServiceType.SDK);
        if (sdkRemote == null) {
            reject(owner, session, userId, "SdkService 未配置", true);
            return;
        }
        RpcResult<SdkValidateResult> sdkResult = SdkServiceRpcProxy.callRequestValidate(
                sdkRemote, userId, request.getSdkToken());
        if (!sdkResult.isSuccess()) {
            reject(owner, session, userId, "SDK 服务不可用", false);
            return;
        }
        SdkValidateResult validateResult = sdkResult.getValue();
        if (validateResult == null || !validateResult.isSuccess()) {
            String message = validateResult == null ? "SDK 校验结果为空" : validateResult.getMessage();
            reject(owner, session, userId, message, false);
            return;
        }
        RpcResult<LobbyUserAccessResult> userResult = LobbyServiceRpcProxy.callValidateOrCreateUser(null, userId);
        if (!userResult.isSuccess()) {
            reject(owner, session, userId, "LobbyService 用户校验失败", true);
            return;
        }
        LobbyUserAccessResult accessResult = userResult.getValue();
        if (accessResult == null || !accessResult.isAllowed()) {
            String message = accessResult == null ? "用户校验结果为空" : accessResult.getMessage();
            reject(owner, session, userId,
                    message == null || message.isBlank() ? "用户不可登录" : message, false);
            return;
        }
        RpcResult<OnlineLoginAdmission> admissionResult = OnlineLoginRpcProxy.callAdmitLogin(
                null, userId, session.getGate(), session.getSessionId());
        if (!admissionResult.isSuccess()) {
            reject(owner, session, userId, "OnlineService 服务不可用", false);
            return;
        }
        OnlineLoginAdmission admission = admissionResult.getValue();
        if (admission == null) {
            rejectAndClose(owner, session, "OnlineService 暂时无法受理登录");
            return;
        }
        if (admission.isQueued()) {
            LogCore.core.info("ConnService 登录进入 Online 排队: service={}, sessionId={}, userId={}",
                    owner.getId(), session.getSessionId(), userId);
            return;
        }
        if (admission.getTokenState() == null) {
            rejectAndClose(owner, session, "OnlineService 登录准入结果非法");
            return;
        }
        OnlineTokenState tokenState = admission.getTokenState();
        LogCore.core.info("ConnService 登录准入成功: service={}, sessionId={}, userId={}, targetGate={}, version={}, expireAt={}",
                owner.getId(), session.getSessionId(), userId, tokenState.getGate(),
                tokenState.getVersion(), tokenState.getExpireAt());
    }

    private void reject(ConnService owner, ClientSessionRef session, String userId,
                        String message, boolean warning) {
        if (warning) {
            LogCore.core.warn("ConnService 首段登录拒绝: service={}, sessionId={}, userId={}, reason={}",
                    owner.getId(), session.getSessionId(), userId, message);
        } else {
            LogCore.core.info("ConnService 首段登录拒绝: service={}, sessionId={}, userId={}, reason={}",
                    owner.getId(), session.getSessionId(), userId, message);
        }
        S2C_Login response = S2C_Login.newBuilder()
                .setSuccess(false).setMessage(message == null ? "登录失败" : message).build();
        owner.pushToClient(session.getSessionId(), ClientFrameChunk.wrap(MsgId.S2C_LOGIN_VALUE, response));
    }

    private void rejectAndClose(ConnService owner, ClientSessionRef session, String reason) {
        S2C_Login response = S2C_Login.newBuilder().setSuccess(false).setMessage(reason).build();
        owner.redirectClient(session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_LOGIN_VALUE, response));
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
