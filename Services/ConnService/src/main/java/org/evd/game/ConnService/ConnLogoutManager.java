package org.evd.game.ConnService;

import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.OnlineService.OnlineLogoutActorProxy;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

/** ConnService 的物理连接关闭、离线通知和会话索引释放。 */
final class ConnLogoutManager {
    private final ConnService owner;
    private final ConnSessionRegistry sessionRegistry;

    ConnLogoutManager(ConnService owner, ConnSessionRegistry sessionRegistry) {
        this.owner = owner;
        this.sessionRegistry = sessionRegistry;
    }

    void kickSession(long sessionId, int brokenTypeCode, String reason) {
        NetChannel session = owner.findClientChannel(sessionId);
        if (session == null) {
            LogCore.core.info("ConnService 踢出连接时目标已不存在: service={}, sessionId={}, brokenType={}, reason={}",
                    owner.getId(), sessionId, BrokenType.fromCode(brokenTypeCode), reason);
            return;
        }
        closeSession(session, brokenTypeCode, reason);
    }

    void closeSession(long sessionId, int brokenTypeCode, String reason) {
        NetChannel session = owner.findClientChannel(sessionId);
        if (session == null) {
            LogCore.core.info("ConnService 关闭连接时目标不存在: service={}, sessionId={}, brokenType={}, reason={}",
                    owner.getId(), sessionId, BrokenType.fromCode(brokenTypeCode), reason);
            return;
        }
        closeSession(session, brokenTypeCode, reason);
    }

    void closeSession(NetChannel session, int brokenTypeCode, String reason) {
        if (!session.beginCloseCleanup()) {
            LogCore.core.info("ConnService 连接已处于关闭状态: service={}, sessionId={}, reason={}",
                    owner.getId(), session.getChannelId(), reason);
            return;
        }
        BrokenType brokenType = BrokenType.fromCode(brokenTypeCode);
        session.setSessionState(NetChannel.SessionState.CLOSING);
        session.setBrokenType(brokenType);
        long sessionId = session.getChannelId();
        long playerId = session.getPlayerId();
        sessionRegistry.removeUserSession(session.getUserId(), sessionId);
        if (sessionRegistry.removePlayerSession(playerId, sessionId)) {
            owner.removePlayerActorAddress(playerId);
        }
        notifySessionOffline(session);
        LogCore.core.info("ConnService 关闭连接: service={}, sessionId={}, brokenType={}, reason={}",
                owner.getId(), session.getChannelId(), brokenType, reason);
        session.close();
    }

    private void notifySessionOffline(NetChannel session) {
        if (session.getUserId().isBlank()) {
            return;
        }
        CallPoint onlineRemote = owner.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        if (onlineRemote == null) {
            LogCore.core.warn("ConnService 未找到 OnlineService，离线通知未发送: service={}, sessionId={}",
                    owner.getId(), session.getChannelId());
            return;
        }
        RpcResult<Void> result = OnlineLogoutActorProxy.sendOnSessionOffline(
                onlineRemote, session.getUserId(), session.getPlayerId(), owner.getCallPoint(),
                session.getChannelId(), session.getBrokenTypeCode());
        if (!result.isSuccess()) {
            LogCore.core.warn("ConnService 离线通知发送失败: service={}, sessionId={}, userId={}, errorCode={}, message={}",
                    owner.getId(), session.getChannelId(), session.getUserId(),
                    result.getErrorCode(), result.getErrorMessage());
        }
    }

}
