package org.evd.game.ConnService.login;

import org.evd.game.ConnService.ConnService;
import org.evd.game.ConnService.session.ConnSessionRegistry;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;

/** ConnService 的登录登记、角色绑定和待登录失败处理。 */
public final class ConnLoginManager {
    private final ConnService owner;
    private final ConnSessionRegistry sessionRegistry;

    /** 创建 ConnService 登录状态管理器。 */
    public ConnLoginManager(ConnService owner, ConnSessionRegistry sessionRegistry) {
        this.owner = owner;
        this.sessionRegistry = sessionRegistry;
    }

    /** 初始化新连接的待登录状态和心跳时间。 */
    public void initialize(NetChannel session, long now) {
        session.setUserId("");
        session.setPlayerId(0L);
        session.setPendingLoginToken("");
        session.setSessionState(NetChannel.SessionState.CONNECTED);
        session.setLastPingTime(now);
    }

    /** 校验网关会话并登记 OnlineService 已确认登录的用户。 */
    public boolean registerLogin(long sessionId, String userId) {
        NetChannel session = owner.findClientChannel(sessionId);
        if (session == null) {
            LogCore.core.info("ConnService GW 登录失败，session 不存在: service={}, sessionId={}, userId={}",
                    owner.getId(), sessionId, userId);
            return false;
        }
        if (session.getSessionState() != NetChannel.SessionState.CONNECTED) {
            LogCore.core.warn("ConnService GW 登录失败，session 状态错误: service={}, sessionId={}, userId={}, state={}",
                    owner.getId(), sessionId, userId, session.getSessionState());
            owner.closeSession(session, BrokenType.SERVER_KICK.getCode(), "GW 登录 session 状态错误");
            return false;
        }
        if (!session.getUserId().isBlank()) {
            LogCore.core.warn("ConnService GW 登录失败，session 已经注册: service={}, sessionId={}, requestUserId={}, boundUserId={}",
                    owner.getId(), sessionId, userId, session.getUserId());
            owner.closeSession(session, BrokenType.SERVER_KICK.getCode(), "GW 重复登录注册");
            return false;
        }
        if (userId == null || userId.isBlank()) {
            LogCore.core.warn("ConnService GW 登录失败，用户参数非法: service={}, sessionId={}, userId={}",
                    owner.getId(), sessionId, userId);
            owner.closeSession(session, BrokenType.SERVER_KICK.getCode(), "GW 登录参数非法");
            return false;
        }

        session.setUserId(userId);
        session.setPlayerId(0L);
        Long replacedSessionId = sessionRegistry.bindUserSession(userId, sessionId);
        session.setSessionState(NetChannel.SessionState.USER_LOGIN_READY);
        session.setLastPingTime(owner.getTimeCurrent());
        LogCore.core.info("ConnService 用户数据登记成功: service={}, sessionId={}, userId={}, replacedSessionId={}, state={}",
                owner.getId(), sessionId, userId, replacedSessionId, session.getSessionState());
        return true;
    }

    /** 校验当前用户会话并绑定玩家。 */
    public ActorAddress bindPlayer(long sessionId, long playerId, ActorAddress playerActorAddress) {
        NetChannel session = owner.findClientChannel(sessionId);
        if (session == null) {
            LogCore.core.warn("ConnService 玩家绑定失败，session 不存在: service={}, sessionId={}, playerId={}",
                    owner.getId(), sessionId, playerId);
            return null;
        }
        if (session.getSessionState() != NetChannel.SessionState.USER_LOGIN_READY) {
            LogCore.core.warn("ConnService 玩家绑定失败，session 状态错误: service={}, sessionId={}, playerId={}, userId={}, state={}",
                    owner.getId(), sessionId, playerId, session.getUserId(), session.getSessionState());
            return null;
        }
        if (!sessionRegistry.isCurrentUserSession(session.getUserId(), sessionId)) {
            LogCore.core.warn("ConnService 玩家绑定失败，session 已不是当前用户会话: service={}, sessionId={}, playerId={}, userId={}",
                    owner.getId(), sessionId, playerId, session.getUserId());
            return null;
        }
        if (playerId <= 0L || playerActorAddress == null) {
            LogCore.core.warn("ConnService 玩家绑定失败，绑定参数非法: service={}, sessionId={}, playerId={}, playerActorAddress={}",
                    owner.getId(), sessionId, playerId, playerActorAddress);
            return null;
        }
        Long currentPlayerSessionId = sessionRegistry.findPlayerSessionIdIfBound(playerId);
        if (currentPlayerSessionId != null && currentPlayerSessionId != sessionId) {
            LogCore.core.warn("ConnService 玩家绑定冲突: service={}, sessionId={}, playerId={}, currentSessionId={}",
                    owner.getId(), sessionId, playerId, currentPlayerSessionId);
            return null;
        }
        ActorAddress gateActorAddress = owner.registerPlayerMailbox(playerId);
        if (gateActorAddress == null) {
            LogCore.core.warn("ConnService 注册 GW 玩家 mailbox 失败: service={}, sessionId={}, playerId={}",
                    owner.getId(), sessionId, playerId);
            return null;
        }
        session.setPlayerId(playerId);
        sessionRegistry.bindPlayerSession(playerId, sessionId);
        ActorId actorId = ActorId.player(playerId);
        owner.getMessageLocationSender().cache(actorId, playerActorAddress);
        LogCore.core.info("ConnService 缓存 PlayerActorAddress: playerId={}, actorId={}, actorAddress={}",
                playerId, actorId, playerActorAddress);
        session.setSessionState(NetChannel.SessionState.PLAYER_LOGIN_READY);
        LogCore.core.info("ConnService 玩家绑定成功: service={}, sessionId={}, userId={}, playerId={}, state={}",
                owner.getId(), sessionId, session.getUserId(), playerId, session.getSessionState());
        return gateActorAddress;
    }

    /** 向待登录连接发送失败响应并结束该预登录连接。 */
    public boolean rejectPendingLogin(NetChannel session, long sessionId, String userId, String token,
                               ClientFrameChunk packet, BrokenType brokenType, String reason) {
        boolean userMatched = session != null && (session.getUserId().isBlank()
                || userId != null && userId.equals(session.getUserId()));
        boolean tokenMatched = session != null && (session.getPendingLoginToken().isBlank()
                || token != null && token.equals(session.getPendingLoginToken()));
        if (session == null || session.isAuthorized()
                || session.getSessionState() != NetChannel.SessionState.CONNECTED
                || !userMatched || !tokenMatched || packet == null) {
            LogCore.core.warn("ConnService 拒绝预登录连接失败: service={}, sessionId={}, requestUserId={}, boundUserId={}, state={}, authorized={}, userMatched={}, tokenMatched={}, reason={}",
                    owner.getId(), sessionId, userId, session == null ? null : session.getUserId(),
                    session == null ? null : session.getSessionState(), session != null && session.isAuthorized(),
                    userMatched, tokenMatched, reason);
            return false;
        }
        session.setBrokenType(brokenType);
        owner.writeClientPacket(sessionId, packet, true);
        LogCore.core.info("ConnService 拒绝预登录连接: service={}, sessionId={}, userId={}, brokenType={}, reason={}",
                owner.getId(), sessionId, userId, brokenType, reason);
        return true;
    }
}
