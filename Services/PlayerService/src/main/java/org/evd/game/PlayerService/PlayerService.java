package org.evd.game.PlayerService;

import org.evd.game.PlayerService.reconcile.PlayerOnlineReconcileS;
import org.evd.game.PlayerService.disconnect.PlayerServiceConnectLogic;
import org.evd.game.PlayerService.disconnect.PlayerServiceDisconnectLogic;
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
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.Collection;
import java.util.List;

public class PlayerService extends Service {
    private final PlayerSessionManager sessionManager;
    private final PlayerOnlineReconcileS playerOnlineReconcileS;
    private final PlayerTimer playerTimer;

    /** 创建 PlayerService，并初始化玩家会话绑定管理器。 */
    public PlayerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.sessionManager = new PlayerSessionManager();
        this.playerOnlineReconcileS = new PlayerOnlineReconcileS(this, sessionManager);
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

    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        LogCore.core.info("PlayerService 开始处理关联服务断开: service={}, count={}", id, serviceList.size());
        getActor(PlayerServiceDisconnectLogic.class).onServiceDisconnect(serviceList);
        LogCore.core.info("PlayerService 完成关联服务断开处理: service={}, count={}", id, serviceList.size());
    }

    /**
     * 子类处理新的 Service 发现事件。
     */
    @Override
    protected void onServiceConnect(Collection<RegisteredService> serviceList) {
        getActor(PlayerServiceConnectLogic.class).onServiceConnect(serviceList);
    }

    @Override
    protected void onServiceConnectReady(Collection<RegisteredService> serviceList) {
        getActor(PlayerServiceConnectLogic.class).onServiceConnectReady(serviceList);
    }

    /** 返回玩家会话状态，供登录和离线逻辑共同使用。 */
    public PlayerSessionManager sessionManager() {
        return sessionManager;
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

    /** 缓存当前玩家所在 Stage 的 ActorAddress，供本服务定位地图玩家消息。 */
    public void cacheStageActorAddress(long playerId, ActorAddress stageActorAddress) {
        getMessageLocationSender().cache(ActorId.mapPlayer(playerId), stageActorAddress);
    }

    /** 删除当前玩家所在 Stage 的 ActorAddress 缓存。 */
    public void removeStageActorAddress(long playerId) {
        getMessageLocationSender().remove(ActorId.mapPlayer(playerId));
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

    /** 返回 MDB 当前仍保留的玩家，用于 PlayerService 上线时向 OnlineService 恢复历史绑定。 */
    public List<String> getMdbPlayerUserIds() {
        return getMdb().getPlayerUserIds();
    }

    public static PlayerService current() {
        return getCurrent(PlayerService.class);
    }
}
