package org.evd.game.ConnService.reconcile;

import org.evd.game.ConnService.ConnService;
import org.evd.game.common.proxy.OnlineService.OnlineStateReconcileRpcProxy;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** GW → Online 对账的 GW 侧实现。 */
public final class GwOnlineReconcileS {
    public static final long INTERVAL_MILLIS = 30_000L;

    private final ConnService owner;

    public GwOnlineReconcileS(ConnService owner) {
        this.owner = owner;
    }

    public void reconcile() {
        Map<String, ConnStateCheck> entries = new HashMap<>();
        for (NetChannel channel : owner.clientChannelManager().getChannelMap().values()) {
            if (channel.getPlayerId() <= 0L || channel.getUserId().isBlank()
                    || channel.getSessionState() != NetChannel.SessionState.PLAYER_LOGIN_READY) {
                continue;
            }
            entries.put(channel.getUserId(), new ConnStateCheck(
                    channel.getUserId(), channel.getPlayerId(), channel.getChannelId()));
        }
        RpcResult<List<ConnStateCheck>> result = OnlineStateReconcileRpcProxy.callReconcileConnSessions(
                null, owner.getCallPoint(), entries);
        if (!result.isSuccess()) {
            LogCore.core.warn("ConnService GW-Online 对账请求失败: service={}, count={}, errorCode={}, message={}",
                    owner.getId(), entries.size(), result.getErrorCode(), result.getErrorMessage());
            return;
        }
        processRelation(entries, result.getValue());
    }

    private void processRelation(Map<String, ConnStateCheck> entries,
                                 List<ConnStateCheck> mismatches) {
        for (ConnStateCheck entry : entries.values()) {
            NetChannel current = owner.findClientChannel(entry.getGateSessionId());
            if (current != null && !containsSameSession(mismatches, entry)) {
                current.clearOnlineReconcileMismatch();
            }
        }
        for (ConnStateCheck entry : mismatches) {
            NetChannel current = owner.findClientChannel(entry.getGateSessionId());
            if (current == null || current.getSessionState() == NetChannel.SessionState.CLOSING) {
                continue;
            }
            if (current.getPlayerId() != entry.getPlayerId()
                    || !entry.getUserId().equals(current.getUserId())) {
                LogCore.core.info("ConnService 忽略过期 GW-Online 对账结果: service={}, sessionId={}",
                        owner.getId(), entry.getGateSessionId());
                continue;
            }
            if (!current.observeOnlineReconcileMismatch()) {
                LogCore.core.info("ConnService GW-Online 首次发现异常: service={}, userId={}, playerId={}, sessionId={}",
                        owner.getId(), entry.getUserId(), entry.getPlayerId(), entry.getGateSessionId());
                continue;
            }
            owner.offlineManager().kickSession(entry.getGateSessionId(),
                    BrokenType.STATE_RECONCILE.getCode(), "online state reconcile mismatch: Online");
        }
    }

    private boolean containsSameSession(List<ConnStateCheck> entries, ConnStateCheck expected) {
        for (ConnStateCheck entry : entries) {
            if (entry.getUserId().equals(expected.getUserId())
                    && entry.getPlayerId() == expected.getPlayerId()
                    && entry.getGateSessionId() == expected.getGateSessionId()) {
                return true;
            }
        }
        return false;
    }
}
