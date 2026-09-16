package org.evd.game.OnlineService.login;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.offline.OnlineOfflineLogic;
import org.evd.game.OnlineService.routing.OnlineRoutingLogic;
import org.evd.game.OnlineService.session.OnlineSessionLogic;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.proto.C2S_Login2;
import org.evd.game.common.proto.AuthMsgId;
import org.evd.game.common.proto.S2C_Login;
import org.evd.game.common.proto.S2C_Login2;
import org.evd.game.common.proxy.ConnService.ConnLoginRpcProxy;
import org.evd.game.common.proxy.ConnService.ConnOfflineRpcProxy;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.LobbyService.LobbyRoleRpcProxy;
import org.evd.game.common.serializeBean.OnlineService.login.SOnlineLoginAdmission;
import org.evd.game.common.serializeBean.OnlineService.login.SOnlineTokenState;
import org.evd.game.common.serializeBean.OnlineService.routing.SOnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.session.SOnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.ymlconfig.LoginYml;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Actor
public final class OnlineLoginLogic {
    private static final long TOKEN_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long LOGIN2_TIMEOUT_MILLIS = 60_000L;

    private final Map<String, SOnlineTokenState> tokenStates = new HashMap<>();
    private final OnlineLoginQueue admissionQueue;
    private final int maxOnline;
    private long nextVersion = System.currentTimeMillis();

    public OnlineLoginLogic() {
        LoginYml loginConfig = GlobalYml.requireNodeConfig().getLogin();
        this.maxOnline = loginConfig.getMaxOnline();
        this.admissionQueue = new OnlineLoginQueue(
                loginConfig.getAdmissionsPerSecond(), loginConfig.getMaxQueueSize());
        LogCore.core.info("OnlineService 登录排队器初始化: maxOnline={}, admissionsPerSecond={}, maxQueueSize={}",
                maxOnline, loginConfig.getAdmissionsPerSecond(), loginConfig.getMaxQueueSize());
    }

    /** 清理已过期的预登录 token 并尝试释放排队中的登录请求。 */
    public void cleanupExpiredTokens(long now) {
        int before = tokenStates.size();
        tokenStates.entrySet().removeIf(entry -> entry.getValue().getExpireAt() <= now);
        int removed = before - tokenStates.size();
        if (removed > 0) {
            LogCore.core.info("OnlineService 清理过期登录 token: service={}, removed={}, remaining={}",
                    owner().getId(), removed, tokenStates.size());
        }
        pumpAdmissionQueue();
    }

    /** 校验登录请求并创建登录准入，容量不足时将请求加入排队。 */
    private SOnlineLoginAdmission admitLogin(String userId, CallPoint requestGate,
                                             long requestSessionId, long now) {
        if (userId == null || userId.isBlank()) {
            LogCore.core.info("OnlineService 拒绝登录准入，userId 为空: service={}", owner().getId());
            return null;
        }
        if (requestGate == null || requestSessionId <= 0L) {
            LogCore.core.warn("OnlineService 拒绝登录准入，原 GW 会话参数非法: userId={}, gate={}, sessionId={}",
                    userId, requestGate, requestSessionId);
            return null;
        }
        boolean existingUser = session().hasUserState(userId) || tokenStates.containsKey(userId);
        if (!existingUser && (isAdmissionBlocked(userId) || admissionQueue.size() > 0)) {
            if (!admissionQueue.offer(userId, requestGate, requestSessionId, this)) {
                LogCore.core.warn("OnlineService 登录排队已满: userId={}, queueSize={}",
                        userId, admissionQueue.size());
                return null;
            }
            LogCore.core.info("OnlineService 登录进入排队: userId={}, position={}, maxOnline={}, reserved={}, queueSize={}",
                    userId, admissionQueue.position(userId), maxOnline,
                    reservedUserCount(), admissionQueue.size());
            return SOnlineLoginAdmission.queued();
        }
        SOnlineLoginAdmission admission = createAdmission(userId, now);
        if (!sendAdmissionResponse(userId, requestGate, requestSessionId, admission)) {
            return null;
        }
        return admission;
    }

    /** 按当前限流预算处理排队登录并通知已获准入的客户端。 */
    public void pumpAdmissionQueue() {
        admissionQueue.pump(owner().getTimeCurrent(), this);
    }

    /** 判断新用户当前是否没有可用登录名额。 */
    public boolean isAdmissionBlocked(String userId) {
        return !session().hasUserState(userId) && !tokenStates.containsKey(userId)
                && reservedUserCount() >= maxOnline;
    }

    /** 创建用户的预登录准入结果。 */
    public SOnlineLoginAdmission createAdmission(String userId, long now) {
        return createAdmissionInternal(userId, now);
    }

    /** 处理同一用户的新排队请求替换旧请求。 */
    public void onReplaced(OnlineLoginQueue.QueuedLogin request) {
        offline().kickGateway(request.gate(), request.sessionId(),
                BrokenType.LOGIN_REPLACE, "duplicate login queued");
    }

    /** 向已经获得名额的排队请求发送准入结果。 */
    public void onAdmissionReady(OnlineLoginQueue.QueuedLogin request,
                                 SOnlineLoginAdmission admission) {
        sendAdmissionResponse(request.userId(), request.gate(), request.sessionId(), admission);
    }

    /** 获取并校验用户当前持有的预登录 token。 */
    public SOnlineTokenState getTokenState(String userId, String token) {
        if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
            return null;
        }
        SOnlineTokenState tokenState = tokenStates.get(userId);
        if (tokenState == null || !userId.equals(tokenState.getUserId())
                || !token.equals(tokenState.getToken())) {
            return null;
        }
        return new SOnlineTokenState(tokenState);
    }

    /** 在二段登录期间按版本和网关校验并续期预登录 token。 */
    public boolean renewPendingLogin(String userId, String token, long version,
                                     CallPoint gate, long now) {
        SOnlineTokenState tokenState = currentToken(userId, token, version, gate, now);
        if (tokenState == null) {
            return false;
        }
        tokenState.setExpireAt(Math.max(tokenState.getExpireAt(), now + LOGIN2_TIMEOUT_MILLIS));
        return true;
    }

    private boolean sendAdmissionResponse(String userId, CallPoint gate, long sessionId,
                                           SOnlineLoginAdmission admission) {
        if (admission == null || admission.getTokenState() == null) {
            LogCore.core.warn("OnlineService 登录准入结果非法: userId={}, gate={}, sessionId={}",
                    userId, gate, sessionId);
            return false;
        }
        S2C_Login response = S2C_Login.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
                .setGateAddr(admission.getGateAddr())
                .setToken(admission.getTokenState().getToken())
                .setTokenExpireAt(admission.getTokenState().getExpireAt())
                .build();
        RpcResult<Void> result = ConnServiceRpcProxy.sendRedirectClient(
                gate, sessionId,
                ClientFrameChunk.wrap(AuthMsgId.S2C_AUTH_LOGIN_VALUE, response));
        if (!result.isSuccess()) {
            cancelPendingSession(userId, admission.getTokenState().getToken());
            LogCore.core.warn("OnlineService 发送登录准入响应失败: userId={}, gate={}, sessionId={}, errorCode={}, message={}",
                    userId, gate, sessionId,
                    result.getErrorCode(), result.getErrorMessage());
            return false;
        }
        return true;
    }

    /** 为用户选择连接网关并生成预登录 token。 */
    private SOnlineLoginAdmission createAdmissionInternal(String userId, long now) {
        SOnlineConnCandidate candidate = routing().selectLeastLoadedConn();
        if (candidate == null || candidate.getCallPoint() == null) {
            return null;
        }
        long version = nextVersion(now);
        SOnlineTokenState tokenState = new SOnlineTokenState(
                UUID.randomUUID().toString(), userId, candidate.getCallPoint(),
                now + TOKEN_TTL_MILLIS, version);
        SOnlineTokenState replaced = tokenStates.put(userId, tokenState);
        if (replaced != null) {
            LogCore.core.info("OnlineService 替换旧预登录: userId={}, oldVersion={}, newVersion={}",
                    userId, replaced.getVersion(), version);
        }
        SOnlineLoginAdmission admission = new SOnlineLoginAdmission(tokenState);
        admission.setGateAddr(candidate.getPublicAddr());
        LogCore.core.info("OnlineService 登录准入成功: userId={}, gate={}, version={}, expireAt={}, gateLoginCount={}",
                userId, candidate.getCallPoint(), version, tokenState.getExpireAt(), candidate.getLoginCount());
        return admission;
    }

    /** 统计预登录状态和正式在线状态的总占用数量。 */
    private int reservedUserCount() {
        int count = tokenStates.size() + session().userStateCount();
        for (String userId : tokenStates.keySet()) {
            if (session().hasUserState(userId)) {
                count--;
            }
        }
        return count;
    }

    /** 校验用户、token、版本、网关及有效期是否仍与当前预登录状态一致。 */
    private SOnlineTokenState currentToken(String userId, String token, long version,
                                           CallPoint gate, long now) {
        if (userId == null || userId.isBlank() || token == null || token.isBlank() || gate == null) {
            return null;
        }
        SOnlineTokenState tokenState = tokenStates.get(userId);
        if (tokenState == null || !token.equals(tokenState.getToken())
                || version > 0L && tokenState.getVersion() != version
                || !userId.equals(tokenState.getUserId())
                || !gate.equals(tokenState.getGate())
                || tokenState.getExpireAt() < now) {
            return null;
        }
        return tokenState;
    }

    /** 生成严格递增的登录状态版本号。 */
    private long nextVersion(long now) {
        nextVersion = Math.max(nextVersion + 1L, now);
        return nextVersion;
    }

    /** 受理首段登录，按容量生成预登录状态或进入队列。 */
    public SOnlineLoginAdmission admitLogin(String userId, CallPoint requestGate, long requestSessionId) {
        return admitLogin(userId, requestGate, requestSessionId, owner().getTimeCurrent());
    }

    /** 取消指定网关会话对应的排队登录请求。 */
    public void cancelQueuedLogin(String userId, CallPoint requestGate, long requestSessionId) {
        if (!admissionQueue.cancel(userId, requestGate, requestSessionId)) {
            return;
        }
        LogCore.core.info("OnlineService 取消排队登录: userId={}, gate={}, sessionId={}",
                userId, requestGate, requestSessionId);
    }

    /** 取消指定 token 对应的预登录状态。 */
    public boolean cancelPendingSession(String userId, String token) {
        SOnlineTokenState tokenState = tokenStates.get(userId);
        if (tokenState == null || token == null
                || !token.equals(tokenState.getToken())
                || !userId.equals(tokenState.getUserId())) {
            return false;
        }
        tokenStates.remove(userId, tokenState);
        LogCore.core.info("OnlineService 取消预登录: userId={}, version={}", userId, tokenState.getVersion());
        return true;
    }

    /**
     * 处理客户端二段登录请求并完成登录状态提交。
     * 这个方法本身幂等;可以重复调用;
     * 所以不怕旧的协议重新发送该方法;而且也没法重复发;
     *  */
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
        SOnlineTokenState tokenState = getTokenState(userId, token);
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
        if (!renewPendingLogin(userId, token, expectedVersion,
                gate, owner.getTimeCurrent())) {
            LogCore.core.info("OnlineService 二段登录拒绝，预登录已变化: userId={}, gateSessionId={}, version={}",
                    userId, gateSessionId, expectedVersion);
            rejectLoginSession(gate, gateSessionId, userId, token, "token 已过期或已被新的登录流程替换");
            return;
        }

        SOnlineTokenState currentToken = getTokenState(userId, token);
        if (currentToken == null || currentToken.getVersion() != expectedVersion
                || currentToken.getGate() == null || !currentToken.getGate().equals(gate)) {
            LogCore.core.info("OnlineService 清理旧会话前预登录已变化: userId={}, gateSessionId={}, version={}",
                    userId, gateSessionId, expectedVersion);
            rejectLoginSession(gate, gateSessionId, userId, token, "token 已过期或已被新的登录流程替换");
            return;
        }

        SOnlineUserState oldUserState = session().getUserState(userId);

        // 先清理旧会话的下游状态，但不在这里等待/踢旧 GW；旧 GW 要在新状态登记后再 call。
        CallPoint oldGate = oldUserState == null ? null : oldUserState.getActiveGate();
        long oldGateSessionId = oldUserState == null ? 0L : oldUserState.getActiveGateSessionId();
        offline().offlineSession(userId, oldGate, oldGateSessionId,
                BrokenType.LOGIN_REPLACE);

        userLogin(gate, gateSessionId, userId, token, expectedVersion, oldUserState);
    }

    /** 统一提交 ONLINE 用户、GW 登记，并在 GW 成功后返回玩家列表。 */
    private void userLogin(CallPoint gate, long gateSessionId,
                           String userId, String token, long version,
                           SOnlineUserState oldUserState) {
        if (!cancelPendingSession(userId, token)) {
            LogCore.core.warn("OnlineService 用户上线提交失败，删除预登录状态失败: userId={}, gate={}, gateSessionId={}, version={}",
                    userId, gate, gateSessionId, version);
            rejectLoginSession(gate, gateSessionId, userId, token, "登录状态提交失败");
            return;
        }

        session().createOnlineState(userId, gate, gateSessionId);

        kickOldGateway(oldUserState);

        SOnlineUserState currentUserState = session().getUserState(userId);
        if (!session().matchesSession(userId, gate, gateSessionId)) {
            LogCore.core.warn("OnlineService 新建用户状态后会话已失效: userId={}, gate={}, gateSessionId={}, version={}, currentState={}",
                    userId, gate, gateSessionId, version, currentUserState);
            return;
        }

        RpcResult<Boolean> registerGateResult = ConnLoginRpcProxy.callRegisterLogin(
                gate, gateSessionId, userId);
        if (!registerGateResult.isSuccess() || !Boolean.TRUE.equals(registerGateResult.getValue())) {
            LogCore.core.warn("OnlineService GW 用户登记失败: userId={}, gate={}, gateSessionId={}, version={}, errorCode={}, message={}, value={}",
                    userId, gate, gateSessionId, version,
                    registerGateResult.getErrorCode(), registerGateResult.getErrorMessage(),
                    registerGateResult.getValue());
            offline().onSessionOffline(
                    userId, 0L, gate, gateSessionId, BrokenType.SERVER_KICK.getCode());
            closeLoginGateway(gate, gateSessionId, "新 GW 用户登记失败");
            return;
        }

        if (!session().matchesSession(userId, gate, gateSessionId)) {
            closeLoginGateway(gate, gateSessionId, "新 GW 登录状态已被替换");
            return;
        }

        RpcResult<Void> roleListResult = LobbyRoleRpcProxy.sendRoleList(
                null, gate, gateSessionId, userId);
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
    private void kickOldGateway(SOnlineUserState oldUserState) {
        if (oldUserState == null || oldUserState.getActiveGate() == null
                || oldUserState.getActiveGateSessionId() <= 0L) {
            return;
        }
        RpcResult<Void> result = ConnOfflineRpcProxy.sendCloseSession(
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
        RpcResult<Void> result = ConnOfflineRpcProxy.sendCloseSession(
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
        RpcResult<Boolean> result = ConnLoginRpcProxy.callRejectPendingLogin(
                gate, gateSessionId, userId, token,
                ClientFrameChunk.wrap(AuthMsgId.S2C_AUTH_LOGIN2_VALUE, response),
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
        RpcResult<Void> result = ConnServiceRpcProxy.sendRedirectClient(
                gate, gateSessionId, ClientFrameChunk.wrap(AuthMsgId.S2C_AUTH_LOGIN2_VALUE, response));
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 回登录失败并关闭连接时发送失败: gate={}, gateSessionId={}, reason={}, errorCode={}, message={}",
                    gate, gateSessionId, reason, result.getErrorCode(), result.getErrorMessage());
        }
    }

    /** 获取当前执行上下文中的 OnlineService 实例。 */
    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }

    private OnlineSessionLogic session() {
        return owner().getActor(OnlineSessionLogic.class);
    }

    private OnlineRoutingLogic routing() {
        return owner().getActor(OnlineRoutingLogic.class);
    }

    private OnlineOfflineLogic offline() {
        return owner().getActor(OnlineOfflineLogic.class);
    }

}
