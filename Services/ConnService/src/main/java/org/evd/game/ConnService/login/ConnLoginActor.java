package org.evd.game.ConnService.login;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proto.C2S_Login;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_Login;
import org.evd.game.common.proxy.LobbyService.LobbyServiceProxy;
import org.evd.game.common.proxy.OnlineService.OnlineLoginActorProxy;
import org.evd.game.common.proxy.SdkService.SdkServiceProxy;
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

/** GW 首段登录：SDK 校验通过后向 OnlineService 申请登录准入。 */
@Actor
public final class ConnLoginActor {
    /** Online 提交正式用户状态后，登记当前网关会话。 */
    @Rpc
    public boolean registerLogin(long sessionId, String userId) {
        return owner().loginManager().registerLogin(sessionId, userId);
    }

    /** 绑定当前授权会话的玩家，并返回 GW 玩家 ActorAddress。 */
    @Rpc
    public ActorAddress bindPlayer(long sessionId, long playerId,
                                   ActorAddress playerActorAddress) {
        return owner().loginManager().bindPlayer(sessionId, playerId, playerActorAddress);
    }

    /** 向待登录客户端返回失败响应并结束预登录连接。 */
    @Rpc
    public boolean rejectPendingLogin(long sessionId, String userId, String token,
                                      ClientFrameChunk packet, int brokenTypeCode, String reason) {
        return owner().loginManager().rejectPendingLogin(
                owner().findClientChannel(sessionId), sessionId, userId, token, packet,
                BrokenType.fromCode(brokenTypeCode), reason);
    }

    /** 处理首段登录，完成 SDK 校验并向 OnlineService 申请准入。 */
    @ClientCmd(MsgId.C2S_LOGIN_VALUE)
    public void login(ClientSessionRef session, C2S_Login request) {
        ConnService owner = owner();
        String userId = request.getUserId().trim();
        if (userId.isEmpty()) {
            reject(owner, session, userId, "userId 不能为空", false);
            return;
        }
        LogCore.core.info("ConnService 收到首段登录: service={}, sessionId={}, userId={}",
                owner.getId(), session.getSessionId(), userId);
        CallPoint sdkRemote = owner.getNode().getAnyCallPointByType(ServiceType.SDK);
        if (sdkRemote == null) {
            reject(owner, session, userId, "SdkService 未配置", true);
            return;
        }

        RpcResult<SdkValidateResult> sdkResult = SdkServiceProxy.callRequestValidate(
                sdkRemote, userId, request.getSdkToken());
        if (!sdkResult.isSuccess()) {
            LogCore.core.warn("ConnService SDK 校验 RPC 失败: service={}, sessionId={}, userId={}, errorCode={}, message={}",
                    owner.getId(), session.getSessionId(), userId,
                    sdkResult.getErrorCode(), sdkResult.getErrorMessage());
            reject(owner, session, userId, "SDK 服务不可用", false);
            return;
        }
        SdkValidateResult validateResult = sdkResult.getValue();
        if (validateResult == null || !validateResult.isSuccess()) {
            String message = validateResult == null ? "SDK 校验结果为空" : validateResult.getMessage();
            reject(owner, session, userId, message, false);
            return;
        }
        RpcResult<LobbyUserAccessResult> userResult = LobbyServiceProxy.callValidateOrCreateUser(
                null, userId);
        if (!userResult.isSuccess()) {
            LogCore.core.warn("ConnService Lobby 用户校验 RPC 失败: service={}, sessionId={}, userId={}, errorCode={}, message={}",
                    owner.getId(), session.getSessionId(), userId,
                    userResult.getErrorCode(), userResult.getErrorMessage());
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

        RpcResult<OnlineLoginAdmission> admissionResult = OnlineLoginActorProxy.callAdmitLogin(
                null, userId, session.getGate(), session.getSessionId());
        if (!admissionResult.isSuccess()) {
            LogCore.core.warn("ConnService 登录准入 RPC 失败: service={}, sessionId={}, userId={}, errorCode={}, message={}",
                    owner.getId(), session.getSessionId(), userId,
                    admissionResult.getErrorCode(), admissionResult.getErrorMessage());
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

    /** 向客户端返回首段登录失败，但保留连接用于可恢复的错误场景。 */
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
                .setSuccess(false)
                .setMessage(message == null ? "登录失败" : message)
                .build();
        owner.pushToClient(session.getSessionId(), ClientFrameChunk.wrap(MsgId.S2C_LOGIN_VALUE, response));
    }

    /** 向客户端返回首段登录失败并关闭当前连接。 */
    private void rejectAndClose(ConnService owner, ClientSessionRef session, String reason) {
        S2C_Login response = S2C_Login.newBuilder()
                .setSuccess(false)
                .setMessage(reason)
                .build();
        owner.redirectClient(session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_LOGIN_VALUE, response));
    }

    /** 获取当前执行此客户端命令的 ConnService 实例。 */
    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
