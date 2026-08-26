package org.evd.game.OnlineService.login;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.routing.OnlineServiceSelector;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.login.OnlineLoginAdmission;
import org.evd.game.common.serializeBean.OnlineService.login.OnlineTokenState;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_Login;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.ymlconfig.LoginYml;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** token 生命周期、登录准入排队与登录提交。 */
public final class OnlineLoginCoordinator {
    private static final long TOKEN_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long LOGIN2_TIMEOUT_MILLIS = 60_000L;

    private final OnlineService owner;
    private final OnlineServiceSelector selector;
    private final OnlineSessionCoordinator sessionCoordinator;
    private final Map<String, OnlineTokenState> tokenStates = new HashMap<>();
    private final OnlineLoginQueue admissionQueue;
    private final int maxOnline;
    private long nextVersion = System.currentTimeMillis();

    /** 根据登录配置初始化登录准入协调器。 */
    public OnlineLoginCoordinator(OnlineService owner, OnlineServiceSelector selector,
                                  OnlineSessionCoordinator sessionCoordinator,
                                  LoginYml loginConfig) {
        this.owner = owner;
        this.selector = selector;
        this.sessionCoordinator = sessionCoordinator;
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
                    owner.getId(), removed, tokenStates.size());
        }
        pumpAdmissionQueue();
    }

    /** 校验登录请求并创建登录准入，容量不足时将请求加入排队。 */
    public OnlineLoginAdmission admitLogin(String userId, CallPoint requestGate,
                                           long requestSessionId, long now) {
        if (userId == null || userId.isBlank()) {
            LogCore.core.info("OnlineService 拒绝登录准入，userId 为空: service={}", owner.getId());
            return null;
        }
        if (requestGate == null || requestSessionId <= 0L) {
            LogCore.core.warn("OnlineService 拒绝登录准入，原 GW 会话参数非法: userId={}, gate={}, sessionId={}",
                    userId, requestGate, requestSessionId);
            return null;
        }
        boolean existingUser = sessionCoordinator.hasUserState(userId) || tokenStates.containsKey(userId);
        if (!existingUser && (!canAdmit(userId) || admissionQueue.size() > 0)) {
            if (!admissionQueue.offer(userId, requestGate, requestSessionId, this)) {
                LogCore.core.warn("OnlineService 登录排队已满: userId={}, queueSize={}",
                        userId, admissionQueue.size());
                return null;
            }
            LogCore.core.info("OnlineService 登录进入排队: userId={}, position={}, maxOnline={}, reserved={}, queueSize={}",
                    userId, admissionQueue.position(userId), maxOnline,
                    reservedUserCount(), admissionQueue.size());
            return OnlineLoginAdmission.queued();
        }
        OnlineLoginAdmission admission = createAdmission(userId, now);
        if (!sendAdmissionResponse(userId, requestGate, requestSessionId, admission)) {
            return null;
        }
        return admission;
    }

    /** 取消指定来源仍在排队中的登录请求。 */
    public void cancelQueuedLogin(String userId, CallPoint requestGate, long requestSessionId) {
        if (!admissionQueue.cancel(userId, requestGate, requestSessionId)) {
            return;
        }
        LogCore.core.info("OnlineService 取消排队登录: userId={}, gate={}, sessionId={}",
                userId, requestGate, requestSessionId);
    }

    /** 按当前限流预算处理排队登录并通知已获准入的客户端。 */
    public void pumpAdmissionQueue() {
        admissionQueue.pump(owner.getTimeCurrent(), this);
    }

    /** 判断用户当前是否可以占用一个登录名额。 */
    public boolean canAdmit(String userId) {
        return sessionCoordinator.hasUserState(userId) || tokenStates.containsKey(userId)
                || reservedUserCount() < maxOnline;
    }

    /** 创建用户的预登录准入结果。 */
    public OnlineLoginAdmission createAdmission(String userId, long now) {
        return createAdmissionInternal(userId, now);
    }

    /** 处理同一用户的新排队请求替换旧请求。 */
    public void onReplaced(OnlineLoginQueue.QueuedLogin request) {
        owner.offlineCoordinator().kickGateway(request.gate(), request.sessionId(),
                BrokenType.LOGIN_REPLACE, "duplicate login queued");
    }

    /** 向已经获得名额的排队请求发送准入结果。 */
    public void onAdmissionReady(OnlineLoginQueue.QueuedLogin request, OnlineLoginAdmission admission) {
        sendAdmissionResponse(request.userId(), request.gate(), request.sessionId(), admission);
    }

    /** 统一向首段登录连接发送准入成功响应。 */
    private boolean sendAdmissionResponse(String userId, CallPoint gate, long sessionId,
                                           OnlineLoginAdmission admission) {
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
                ClientFrameChunk.wrap(MsgId.S2C_LOGIN_VALUE, response));
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
    private OnlineLoginAdmission createAdmissionInternal(String userId, long now) {
        OnlineConnCandidate candidate = selector.selectLeastLoadedConn();
        if (candidate == null || candidate.getCallPoint() == null) {
            return null;
        }
        long version = nextVersion(now);
        OnlineTokenState tokenState = new OnlineTokenState(
                UUID.randomUUID().toString(), userId, candidate.getCallPoint(),
                now + TOKEN_TTL_MILLIS, version);
        OnlineTokenState replaced = tokenStates.put(userId, tokenState);
        if (replaced != null) {
            LogCore.core.info("OnlineService 替换旧预登录: userId={}, oldVersion={}, newVersion={}",
                    userId, replaced.getVersion(), version);
        }
        OnlineLoginAdmission admission = new OnlineLoginAdmission(tokenState);
        admission.setGateAddr(candidate.getPublicAddr());
        LogCore.core.info("OnlineService 登录准入成功: userId={}, gate={}, version={}, expireAt={}, gateLoginCount={}",
                userId, candidate.getCallPoint(), version, tokenState.getExpireAt(), candidate.getLoginCount());
        return admission;
    }

    /** 统计预登录状态和正式在线状态的总占用数量。 */
    private int reservedUserCount() {
        int count = tokenStates.size() + sessionCoordinator.userStateCount();
        for (String userId : tokenStates.keySet()) {
            if (sessionCoordinator.hasUserState(userId)) {
                count--;
            }
        }
        return count;
    }

    /** 获取并校验用户当前持有的预登录 token。 */
    public OnlineTokenState getTokenState(String userId, String token) {
        if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
            return null;
        }
        OnlineTokenState tokenState = tokenStates.get(userId);
        if (tokenState == null || !userId.equals(tokenState.getUserId())
                || !token.equals(tokenState.getToken())) {
            return null;
        }
        return new OnlineTokenState(tokenState);
    }

    /** 在二段登录期间按版本和网关校验并续期预登录 token。 */
    public boolean renewPendingLogin(String userId, String token, long version, CallPoint gate, long now) {
        OnlineTokenState tokenState = currentToken(userId, token, version, gate, now);
        if (tokenState == null) {
            return false;
        }
        tokenState.setExpireAt(Math.max(tokenState.getExpireAt(), now + LOGIN2_TIMEOUT_MILLIS));
        return true;
    }

    /** 取消指定 token 对应的预登录会话并继续处理登录队列。 */
    public boolean cancelPendingSession(String userId, String token) {
        OnlineTokenState tokenState = tokenStates.get(userId);
        if (tokenState == null || token == null
                || !token.equals(tokenState.getToken())
                || !userId.equals(tokenState.getUserId())) {
            return false;
        }
        tokenStates.remove(userId, tokenState);
        LogCore.core.info("OnlineService 取消预登录: userId={}, version={}", userId, tokenState.getVersion());
        return true;
    }

    /** 校验用户、token、版本、网关及有效期是否仍与当前预登录状态一致。 */
    private OnlineTokenState currentToken(String userId, String token, long version,
                                          CallPoint gate, long now) {
        if (userId == null || userId.isBlank() || token == null || token.isBlank() || gate == null) {
            return null;
        }
        OnlineTokenState tokenState = tokenStates.get(userId);
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
}
