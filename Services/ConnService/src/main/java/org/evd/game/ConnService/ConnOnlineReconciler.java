package org.evd.game.ConnService;

import org.evd.game.common.proxy.OnlineService.OnlineStateReconcileActorProxy;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;

/** ConnService 的玩家连接状态对账，负责发现并关闭 GW 残留 Session。 */
public final class ConnOnlineReconciler {
    public static final long INTERVAL_MILLIS = 30_000L;

    private final ConnService owner;

    public ConnOnlineReconciler(ConnService owner) {
        this.owner = owner;
    }

    /** 将 GW 当前玩家连接交给 Online 校验，并只关闭返回快照对应的原 Session。 */
    public void reconcile() {
        List<ConnStateCheck> entries = new ArrayList<>();
        for (NetChannel channel : owner.clientChannelManager().snapshotChannels()) {
            if (channel.getPlayerId() <= 0L || channel.getUserId().isBlank()) {
                continue;
            }
            entries.add(new ConnStateCheck(
                    channel.getUserId(), channel.getPlayerId(), channel.getChannelId()));
        }
        if (entries.isEmpty()) {
            return;
        }

        RpcResult<ConnStateCheck[]> result =
                OnlineStateReconcileActorProxy.callReconcileConnSessions(
                        null, owner.getCallPoint(), entries);
        if (!result.isSuccess()) {
            LogCore.core.warn("ConnService 对账请求失败: service={}, target=OnlineService, count={}, errorCode={}, message={}",
                    owner.getId(), entries.size(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }

        ConnStateCheck[] invalidEntries = result.getValue();
        if (invalidEntries.length == 0) {
            return;
        }
        for (ConnStateCheck entry : invalidEntries) {
            NetChannel current = owner.findClientChannel(entry.getGateSessionId());
            if (current == null) {
                continue;
            }
            if (current.getPlayerId() != entry.getPlayerId()
                    || !entry.getUserId().equals(current.getUserId())) {
                LogCore.core.info("ConnService 忽略过期对账结果: service={}, sessionId={}, expectedUserId={}, currentUserId={}, expectedPlayerId={}, currentPlayerId={}",
                        owner.getId(), entry.getGateSessionId(), entry.getUserId(), current.getUserId(),
                        entry.getPlayerId(), current.getPlayerId());
                continue;
            }
            LogCore.core.warn("ConnService 对账发现残留玩家连接，关闭原 Session: service={}, userId={}, playerId={}, sessionId={}",
                    owner.getId(), entry.getUserId(), entry.getPlayerId(), entry.getGateSessionId());
            owner.offlineManager().kickSession(entry.getGateSessionId(),
                    BrokenType.STATE_RECONCILE.getCode(), "online state reconcile mismatch");
        }
    }
}
