package org.evd.game.PlayerService;

import org.evd.game.PlayerService.player.PlayerDataRepository;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proto.RoleData;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.CoroutineLockTimeoutException;

public class PlayerService extends Service {
    private final PlayerSessionManager sessionManager;
    private final PlayerDataRepository playerDataRepository;

    /** 创建 PlayerService，并初始化玩家会话绑定管理器。 */
    public PlayerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.sessionManager = new PlayerSessionManager(this);
        this.playerDataRepository = new PlayerDataRepository();
    }

    /** 执行 PlayerService 的周期性服务任务。 */
    @Override
    public void tick() {
        super.tick();
    }

    /** 校验玩家未上线后加载数据、注册玩家 Actor/Location，再建立在线玩家绑定。 */
    @Rpc
    public ActorAddress loginPlayer(String userId, RoleData role, ClientSessionRef session) {
        if (userId == null || userId.isBlank() || role == null || role.getPlayerId() <= 0L
                || session == null || session.getGate() == null || session.getSessionId() <= 0L) {
            LogCore.core.warn("PlayerService 玩家登录参数非法: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    id, userId, role == null ? 0L : role.getPlayerId(),
                    session == null ? null : session.getGate(),
                    session == null ? 0L : session.getSessionId());
            return null;
        }
        long playerId = role.getPlayerId();
        if (sessionManager.hasOnlinePlayer(playerId)) {
            LogCore.core.error("PlayerService 玩家已经上线: service={}, userId={}, playerId={}",
                    id, userId, playerId);
            return null;
        }
        ActorId actorId = ActorId.player(playerId);
        if (hasActor(actorId)) {
            LogCore.core.error("PlayerService 玩家 Actor 已存在，拒绝重复上线: service={}, userId={}, playerId={}, actorId={}",
                    id, userId, playerId, actorId);
            return null;
        }

        registerActor(actorId, MailBoxType.ORDERED);
        ActorAddress actorAddress = getActorAddress(actorId);
        LogCore.core.info("PlayerService 缓存 PlayerActorAddress: service={}, playerId={}, actorId={}, actorAddress={}",
                id, playerId, actorId, actorAddress);
        sessionManager.bindPlayerSession(userId, playerId, session, actorAddress);

        playerDataRepository.loadOrCreate(playerId, role.getName(), role.getLevel());
        return actorAddress;

    }


    /** 接收正式上线通知，并由 PlayerService 自己推进玩家进入地图。 */
    @Rpc
    public void onlinePlayer(String userId, long playerId, ClientSessionRef session,
                             ActorAddress gateActorAddress) {
        try (ContinuationLockScope ignored = awaitCoroutineLockScope(LockType.ACTOR, ActorId.player(playerId))) {
            if (!sessionManager.bindGateActorAddress(userId, playerId, session, gateActorAddress)) {
                LogCore.core.warn("PlayerService 忽略失效玩家正式上线通知: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                        id, userId, playerId, session == null ? null : session.getGate(),
                        session == null ? 0L : session.getSessionId());
                return;
            }
            getMessageLocationSender().cache(ActorId.gate(playerId), gateActorAddress);
            LogCore.core.info("PlayerService 缓存 GWActorAddress: service={}, playerId={}, actorId={}, actorAddress={}",
                    id, playerId, ActorId.gate(playerId), gateActorAddress);
            LogCore.core.info("PlayerService 玩家正式上线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    id, userId, playerId, session.getGate(), session.getSessionId());
            enterMap(playerId);
        } catch (CoroutineLockTimeoutException e) {
            LogCore.core.warn("PlayerService 玩家正式上线协程锁等待超时: service={}, userId={}, playerId={}, timeoutMillis={}",
                    id, userId, playerId, e.getTimeoutMillis());
        }
    }


    /** 在玩家 actor 锁内处理玩家下线并注销 Player Actor/Location。 */
    @Rpc
    public void onPlayerOffline(String userId, long playerId, CallPoint gate,
                                long gateSessionId, int brokenTypeCode) {
        if (!sessionManager.onPlayerOffline(userId, playerId, gate, gateSessionId, brokenTypeCode)) {
            return;
        }
        ActorId actorId = ActorId.player(playerId);
        ActorAddress actorAddress = getActorAddress(actorId);
        ActorId gateActorId = ActorId.gate(playerId);
        getMessageLocationSender().remove(gateActorId);
        LogCore.core.info("PlayerService 删除 ActorAddress 缓存: service={}, playerId={}, playerActorId={}, gateActorId={}",
                id, playerId, actorId, gateActorId);
        unregisterActor(actorId);
        LogCore.core.info("PlayerService 删除玩家 ActorAddress: service={}, userId={}, playerId={}, actorAddress={}",
                id, userId, playerId, actorAddress);

    }

    /** 执行登录完成后的玩家进入地图入口。 */
    @Rpc
    public void enterMap(long playerId) {
        LogCore.core.info("PlayerService 进入地图占位: service={}, playerId={}", id, playerId);
    }

    /** 返回当前 PlayerService 已绑定的在线玩家数量。 */
    @Rpc
    public int getOnlineCount() {
        return sessionManager.getOnlineCount();
    }

    /** 声明 PlayerService 支持 MDB 消息分发。 */
    @Override
    protected boolean supportMdb() {
        return true;
    }
}
