package org.evd.game.PlayerService.login;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.player.PlayerDataRepository;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.common.proto.RoleData;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.CoroutineLockTimeoutException;

/** PlayerService 登录阶段的玩家会话绑定管理。 */
public final class PlayerLoginManager {
    private final PlayerService owner;
    private final PlayerSessionManager sessionManager;
    private final PlayerDataRepository playerDataRepository;

    public PlayerLoginManager(PlayerService owner, PlayerSessionManager sessionManager,
                              PlayerDataRepository playerDataRepository) {
        this.owner = owner;
        this.sessionManager = sessionManager;
        this.playerDataRepository = playerDataRepository;
    }

    /** 校验玩家登录请求，创建玩家 Actor、绑定会话并加载玩家数据。 */
    public ActorAddress loginPlayer(String userId, RoleData role, ClientSessionRef session) {
        if (userId == null || userId.isBlank() || role == null || role.getPlayerId() <= 0L
                || session == null || session.getGate() == null || session.getSessionId() <= 0L) {
            LogCore.core.warn("PlayerService 玩家登录参数非法: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    owner.getId(), userId, role == null ? 0L : role.getPlayerId(),
                    session == null ? null : session.getGate(),
                    session == null ? 0L : session.getSessionId());
            return null;
        }
        long playerId = role.getPlayerId();
        if (hasOnlinePlayer(playerId)) {
            LogCore.core.error("PlayerService 玩家已经上线: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId);
            return null;
        }
        if (owner.hasPlayerActor(playerId)) {
            LogCore.core.error("PlayerService 玩家 Actor 已存在，拒绝重复上线: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId);
            return null;
        }

        ActorAddress actorAddress = owner.registerPlayerActor(playerId);
        bindPlayerSession(userId, playerId, session, actorAddress);
        playerDataRepository.loadOrCreate(playerId, role.getName(), role.getLevel());
        return actorAddress;
    }

    /** 绑定 GW 玩家 ActorAddress，完成 PlayerService 正式上线并进入地图。 */
    public void onlinePlayer(String userId, long playerId, ClientSessionRef session,
                             ActorAddress gateActorAddress) {
        try (ContinuationLockScope ignored = owner.awaitCoroutineLockScope(
                LockType.ACTOR, ActorId.player(playerId))) {
            if (!bindGateActorAddress(userId, playerId, session, gateActorAddress)) {
                LogCore.core.warn("PlayerService 忽略失效玩家正式上线通知: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                        owner.getId(), userId, playerId, session == null ? null : session.getGate(),
                        session == null ? 0L : session.getSessionId());
                return;
            }
            owner.getMessageLocationSender().cache(ActorId.gate(playerId), gateActorAddress);
            LogCore.core.info("PlayerService 缓存 GWActorAddress: service={}, playerId={}, actorId={}, actorAddress={}",
                    owner.getId(), playerId, ActorId.gate(playerId), gateActorAddress);
            LogCore.core.info("PlayerService 玩家正式上线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    owner.getId(), userId, playerId, session.getGate(), session.getSessionId());
            owner.enterMap(playerId);
        } catch (CoroutineLockTimeoutException e) {
            LogCore.core.warn("PlayerService 玩家正式上线协程锁等待超时: service={}, userId={}, playerId={}, timeoutMillis={}",
                    owner.getId(), userId, playerId, e.getTimeoutMillis());
        }
    }

    /** 判断玩家是否已在当前 PlayerService 建立登录绑定。 */
    public boolean hasOnlinePlayer(long playerId) {
        return sessionManager.hasOnlinePlayer(playerId);
    }

    /** 建立玩家登录绑定，正式上线通知会在后续阶段补充 GW ActorAddress。 */
    public void bindPlayerSession(String userId, long playerId, ClientSessionRef session,
                                  ActorAddress actorAddress) {
        sessionManager.bindPlayerSession(userId, playerId, session, actorAddress);
        LogCore.core.info("PlayerService 绑定玩家: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                owner.getId(), userId, playerId, session.getGate(), session.getSessionId());
    }

    /** 登记正式上线阶段返回的 GW 玩家 ActorAddress。 */
    public boolean bindGateActorAddress(String userId, long playerId, ClientSessionRef session,
                                        ActorAddress gateActorAddress) {
        return sessionManager.bindGateActorAddress(userId, playerId, session, gateActorAddress);
    }

    /** 返回当前 PlayerService 已建立登录绑定的玩家数量。 */
    public int getOnlineCount() {
        return sessionManager.getOnlineCount();
    }
}
