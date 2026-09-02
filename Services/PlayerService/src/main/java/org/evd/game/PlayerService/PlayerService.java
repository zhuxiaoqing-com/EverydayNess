package org.evd.game.PlayerService;

import org.evd.game.PlayerService.offline.PlayerOfflineManager;
import org.evd.game.PlayerService.player.PlayerDataRepository;
import org.evd.game.PlayerService.reconcile.PlayerOnlineReconcileS;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.PlayerService.timer.PlayerTimer;
import org.evd.game.common.proxy.OnlineService.OnlineSessionRpcProxy;
import org.evd.game.runtime.Db.table.MdbPlayerInfo;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.List;

public class PlayerService extends Service {
    private final PlayerSessionManager sessionManager;
    private final PlayerDataRepository playerDataRepository;
    private final PlayerOfflineManager offlineManager;
    private final PlayerOnlineReconcileS playerOnlineReconcileS;
    private final PlayerTimer playerTimer;

    /** 创建 PlayerService，并初始化玩家会话绑定管理器。 */
    public PlayerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.sessionManager = new PlayerSessionManager();
        this.playerDataRepository = new PlayerDataRepository();
        this.offlineManager = new PlayerOfflineManager(this, sessionManager);
        this.playerOnlineReconcileS = new PlayerOnlineReconcileS(this, sessionManager, this.offlineManager);
        this.playerTimer = new PlayerTimer(this);
    }

    @Override
    public void init() {
        super.init();
        getMdb().setPlayerCacheExpiredCallback(this::onPlayerCacheExpired);
        newRepeatedTimer(PlayerTimer.INTERVAL_MILLIS, false, playerTimer::onSecond);
        newRepeatedTimerCoroutine(PlayerOnlineReconcileS.INTERVAL_MILLIS, false, playerOnlineReconcileS::reconcile);
    }

    /** MDB 玩家缓存过期后通知 OnlineService 删除历史 PlayerService 绑定。 */
    private void onPlayerCacheExpired(MdbPlayerInfo info) {
        String userId = info.getUserId();
        RpcResult<Void> result = OnlineSessionRpcProxy.sendRemoveHistoricalPlayerService(
                null, userId, getCallPoint());
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 通知 OnlineService 删除历史绑定失败: service={}, userId={}, errorCode={}, message={}",
                    getId(), userId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    /** 执行 PlayerService 的周期性服务任务。 */
    @Override
    public void tick() {
        super.tick();
    }

    /** 返回玩家会话状态，供登录 RPC Actor 和离线管理器共同使用。 */
    public PlayerSessionManager sessionManager() {
        return sessionManager;
    }

    /** 返回玩家数据仓库，供登录 RPC Actor 加载玩家数据。 */
    public PlayerDataRepository playerDataRepository() {
        return playerDataRepository;
    }

    /** 返回玩家离线流程管理器，供离线 Actor 委托业务处理。 */
    public PlayerOfflineManager offlineManager() {
        return offlineManager;
    }

    public PlayerOnlineReconcileS playerOnlineReconcileS() {
        return playerOnlineReconcileS;
    }


    /** 判断玩家 Actor 是否已经注册。 */
    public boolean hasPlayerActor(long playerId) {
        return hasActor(ActorId.player(playerId));
    }

    /** 注册玩家 Actor 并返回其地址。 */
    public ActorAddress registerPlayerActor(long playerId) {
        ActorId actorId = ActorId.player(playerId);
        registerActor(actorId, MailBoxType.ORDERED);
        ActorAddress actorAddress = getActorAddress(actorId);
        LogCore.core.info("PlayerService 缓存 PlayerActorAddress: service={}, playerId={}, actorId={}, actorAddress={}",
                id, playerId, actorId, actorAddress);
        return actorAddress;
    }

    /** 删除玩家 Actor 和对应的 Location 地址，由离线管理器调用。 */
    public void removePlayerActorState(long playerId) {
        ActorId actorId = ActorId.player(playerId);
        ActorAddress actorAddress = getActorAddress(actorId);
        ActorId gateActorId = ActorId.gate(playerId);
        getMessageLocationSender().remove(gateActorId);
        LogCore.core.info("PlayerService 删除 ActorAddress 缓存: service={}, playerId={}, playerActorId={}, gateActorId={}",
                id, playerId, actorId, gateActorId);
        unregisterActor(actorId);
        LogCore.core.info("PlayerService 删除玩家 ActorAddress: service={}, playerId={}, actorAddress={}",
                id, playerId, actorAddress);

    }

    /** 返回当前 PlayerService 已绑定的在线玩家数量。 */
    public int getOnlineCount() {
        return sessionManager.getOnlineCount();
    }

    /** 返回 MDB 当前仍保留的玩家，用于 OnlineService 重启后恢复历史绑定。 */
    public List<String> getMdbPlayerUserIds() {
        return getMdb().getPlayerUserIds();
    }

}
