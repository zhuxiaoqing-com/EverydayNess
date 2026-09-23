package org.evd.game.ConnService;

import org.evd.game.ConnService.client.ConnClientConnection;
import org.evd.game.ConnService.disconnect.ConnServiceConnectLogic;
import org.evd.game.ConnService.disconnect.ConnServiceDisconnectLogic;
import org.evd.game.ConnService.login.ConnLoginManager;
import org.evd.game.ConnService.offline.ConnOfflineManager;
import org.evd.game.ConnService.reconcile.GwOnlineReconcileS;
import org.evd.game.ConnService.routing.ConnPlayerActorAddressRegistry;
import org.evd.game.ConnService.session.ConnSessionRegistry;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

public class ConnService extends Service {
    private static final long HEARTBEAT_SCAN_INTERVAL_MILLIS = 5_000L;

    private final ConnLoginManager loginManager;
    private final ConnOfflineManager offlineManager;
    private final ConnClientConnection clientConnection;
    private final ConnPlayerActorAddressRegistry playerActorAddressRegistry;
    private final GwOnlineReconcileS gwOnlineReconcileS;

    public ConnService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        ConnSessionRegistry sessionRegistry = new ConnSessionRegistry();
        this.loginManager = new ConnLoginManager(this, sessionRegistry);
        this.offlineManager = new ConnOfflineManager(this, sessionRegistry);
        this.clientConnection = new ConnClientConnection(this, sessionRegistry);
        this.playerActorAddressRegistry = new ConnPlayerActorAddressRegistry(this);
        this.gwOnlineReconcileS = new GwOnlineReconcileS(this);
    }

    @Override
    public void init() {
        super.init();
        LogCore.core.info("ConnService Init");
        clientConnection.init();
        newRepeatedTimer(HEARTBEAT_SCAN_INTERVAL_MILLIS, false, clientConnection::scanHeartbeatTimeouts);
        newRepeatedTimerCoroutine(GwOnlineReconcileS.INTERVAL_MILLIS, false, gwOnlineReconcileS::reconcile);
    }

    public String getPublicAddr() {
        return serviceInfo == null ? "" : serviceInfo.getPublicAddr();
    }

    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        LogCore.core.info("ConnService 开始处理关联服务断开: service={}, count={}", id, serviceList.size());
        getActor(ConnServiceDisconnectLogic.class).onServiceDisconnect(serviceList);
        LogCore.core.info("ConnService 完成关联服务断开处理: service={}, count={}", id, serviceList.size());
    }

    @Override
    protected void onServiceConnect(Collection<RegisteredService> serviceList) {
        getActor(ConnServiceConnectLogic.class).onServiceConnect(serviceList);
    }

    /** 在 GW 注册玩家 mailbox，并将其 ActorAddress 发布到全局 LocationService。 */
    public ActorAddress registerPlayerMailbox(long playerId) {
        if (playerId <= 0L) {
            return null;
        }
        ActorId actorId = ActorId.gate(playerId);
        if (!hasActor(actorId)) {
            registerActor(actorId, MailBoxType.UNORDERED);
            LogCore.core.info("ConnService 玩家 GW mailbox 注册成功: service={}, playerId={}, actorAddress={}",
                    id, playerId, getActorAddress(actorId));
        }
        return getActorAddress(actorId);
    }

    /** 返回玩家当前已注册的 GW mailbox 地址，不自动创建 mailbox。 */
    public ActorAddress getGateActorAddress(long playerId) {
        return playerId <= 0L ? null : getActorAddress(ActorId.gate(playerId));
    }

    /** 删除玩家的 GW mailbox 和关联的 PlayerService ActorAddress 缓存。 */
    public void removePlayerActorAddress(long playerId) {
        if (playerId <= 0L) {
            return;
        }
        ActorId playerActorId = ActorId.player(playerId);
        getMessageLocationSender().remove(playerActorId);
        getMessageLocationSender().remove(ActorId.mapPlayer(playerId));
        LogCore.core.info("ConnService 删除 PlayerActorAddress 缓存: playerId={}, playerActorId={}",
                playerId, playerActorId);
        ActorId gateActorId = ActorId.gate(playerId);
        if (hasActor(gateActorId)) {
            ActorAddress actorAddress = getActorAddress(gateActorId);
            unregisterActor(gateActorId);
            LogCore.core.info("ConnService 删除玩家 GW ActorAddress: service={}, playerId={}, actorAddress={}",
                    id, playerId, actorAddress);
        }
    }

    @Override
    public void onClose() {
        clientConnection.onClose();
        super.onClose();
    }

    /** 统一执行网关连接关闭、离线通知和资源清理。 */
    public void closeSession(long sessionId, int brokenTypeCode, String reason) {
        offlineManager.closeSession(sessionId, brokenTypeCode, reason);
    }

    /** 返回登录流程管理器，供 Conn 登录 Actor 委托业务处理。 */
    public ConnLoginManager loginManager() {
        return loginManager;
    }

    /** 返回离线流程管理器，供 Conn 离线 Actor 委托业务处理。 */
    public ConnOfflineManager offlineManager() {
        return offlineManager;
    }

    public ConnClientConnection clientConnection() {
        return clientConnection;
    }

    public ConnPlayerActorAddressRegistry playerActorAddressRegistry() {
        return playerActorAddressRegistry;
    }

    /** 在 ConnService 协程上下文中统一处理指定连接的关闭清理。 */
    public void closeSession(NetChannel session, int brokenTypeCode, String reason) {
        offlineManager.closeSession(session, brokenTypeCode, reason);
    }
}
