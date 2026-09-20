package org.evd.game.ConnService.offline;

import org.evd.game.ConnService.ConnService;
import org.evd.game.ConnService.session.ConnSessionRegistry;
import org.evd.game.common.proxy.OnlineService.OnlineOfflineRpcProxy;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

/** ConnService 的物理连接关闭、离线通知和会话索引释放。 */
public final class ConnOfflineManager {
    private final ConnService owner;
    private final ConnSessionRegistry sessionRegistry;

    public ConnOfflineManager(ConnService owner, ConnSessionRegistry sessionRegistry) {
        this.owner = owner;
        this.sessionRegistry = sessionRegistry;
    }

    public void kickSession(long sessionId, int brokenTypeCode, String reason) {
        NetChannel session = owner.clientConnection().findClientChannel(sessionId);
        if (session == null) {
            LogCore.core.info("ConnService 踢出连接时目标已不存在: service={}, sessionId={}, brokenType={}, reason={}",
                    owner.getId(), sessionId, BrokenType.fromCode(brokenTypeCode), reason);
            return;
        }
        closeSession(session, brokenTypeCode, reason);
    }

    public void closeSession(long sessionId, int brokenTypeCode, String reason) {
        NetChannel session = owner.clientConnection().findClientChannel(sessionId);
        if (session == null) {
            LogCore.core.info("ConnService 关闭连接时目标不存在: service={}, sessionId={}, brokenType={}, reason={}",
                    owner.getId(), sessionId, BrokenType.fromCode(brokenTypeCode), reason);
            return;
        }
        closeSession(session, brokenTypeCode, reason);
    }

    public void closeSession(NetChannel session, int brokenTypeCode, String reason) {
        if (!session.beginCloseCleanup()) {
            LogCore.core.info("ConnService 连接已处于关闭状态: service={}, sessionId={}, userId={}, playerId={}, reason={}",
                    owner.getId(), session.getChannelId(), session.getUserId(), session.getPlayerId(), reason);
            return;
        }
        BrokenType brokenType = BrokenType.fromCode(brokenTypeCode);
        NetChannel.SessionState sessionState = session.getSessionState();
        session.setSessionState(NetChannel.SessionState.CLOSING);
        session.setBrokenType(brokenType);
        long sessionId = session.getChannelId();
        long playerId = session.getPlayerId();
        sessionRegistry.removeUserSession(session.getUserId(), sessionId);
        if (sessionRegistry.removePlayerSession(playerId, sessionId)) {
            owner.removePlayerActorAddress(playerId);
        }
        notifySessionOffline(session);
        LogCore.core.info("ConnService 关闭连接: service={}, sessionId={}, userId={}, playerId={}, sessionState={}, brokenType={}, reason={}",
                owner.getId(), session.getChannelId(), session.getUserId(), session.getPlayerId(),
                sessionState, brokenType, reason);
        session.close();
    }

    private void notifySessionOffline(NetChannel session) {
        if (session.getUserId().isBlank()) {
            LogCore.core.info("ConnService 跳过离线通知: service={}, sessionId={}, userId={}, playerId={}, reason=用户尚未登录",
                    owner.getId(), session.getChannelId(), session.getUserId(), session.getPlayerId());
            return;
        }
        RpcResult<Void> result = OnlineOfflineRpcProxy.sendOnSessionOffline(
                null, session.getUserId(), session.getPlayerId(), owner.getCallPoint(),
                session.getChannelId(), session.getBrokenTypeCode());
        if (!result.isSuccess()) {
            LogCore.core.warn("ConnService 离线通知发送失败: service={}, sessionId={}, userId={}, playerId={}, errorCode={}, message={}",
                    owner.getId(), session.getChannelId(), session.getUserId(), session.getPlayerId(),
                    result.getErrorCode(), result.getErrorMessage());
            // 这里不再直接补发 PlayerService 离线通知：OnlineService 断开时，PlayerService
            // 会通过自己的 onServiceDisconnect 在本地收敛全部玩家；PlayerService 自身断开时，
            // 直接 RPC 也没有可用目标，ConnService 只负责本地关闭 GW 会话。
        }
    }

}
