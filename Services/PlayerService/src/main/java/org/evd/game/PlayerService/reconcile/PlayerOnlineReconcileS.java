package org.evd.game.PlayerService.reconcile;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.offline.PlayerOfflineManager;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.common.proxy.OnlineService.OnlineStateReconcileRpcProxy;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** PlayerService 的玩家运行态对账，负责清理 Online 已不存在的玩家实例。 */
public final class PlayerOnlineReconcileS {
    public static final long INTERVAL_MILLIS = 30_000L;

    private final PlayerService owner;
    private final PlayerSessionManager sessionManager;
    private final PlayerOfflineManager offlineManager;

    public PlayerOnlineReconcileS(PlayerService owner, PlayerSessionManager sessionManager,
                                  PlayerOfflineManager offlineManager) {
        this.owner = owner;
        this.sessionManager = sessionManager;
        this.offlineManager = offlineManager;
    }

    /** 将 PlayerService 当前运行态交给 Online 校验，并按原会话精确清理失效玩家。 */
    public void reconcile() {
        List<PlayerStateCheck> entries = new ArrayList<>();
        for (PPlayerOnline binding : sessionManager.onlinePlayers()) {
            if (binding.getStatus() != PPlayerOnline.Status.ONLINE) {
                continue;
            }
            entries.add(new PlayerStateCheck(
                    binding.getUserId(), binding.getPlayerId(), binding.getGate(),
                    binding.getGateSessionId()));
        }
        RpcResult<PlayerStateCheck[]> result =
                OnlineStateReconcileRpcProxy.callReconcilePlayerSessions(
                        null, owner.getCallPoint(), entries);
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 对账请求失败: service={}, target=OnlineService, count={}, errorCode={}, message={}",
                    owner.getId(), entries.size(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }

        PlayerStateCheck[] invalidEntries = result.getValue();
        for (PlayerStateCheck entry : entries) {
            PPlayerOnline current = sessionManager.get(entry.getPlayerId());
            if (current == null) {
                continue;
            }
            if (!containsSameSession(invalidEntries, entry)) {
                current.clearOnlineReconcileMismatch();
            }
        }
        processMismatches(invalidEntries);
    }


    private void processMismatches(PlayerStateCheck[] mismatches) {
        for (PlayerStateCheck entry : mismatches) {
            PPlayerOnline current = sessionManager.get(entry.getPlayerId());
            if (current == null || current.getStatus() != PPlayerOnline.Status.ONLINE
                    || !entry.getUserId().equals(current.getUserId())
                    || entry.getGateSessionId() != current.getGateSessionId()
                    || !entry.getGate().equals(current.getGate())) {
                continue;
            }
            boolean shouldRepair = current.observeOnlineReconcileMismatch();
            if (!shouldRepair) {
                LogCore.core.info("PlayerService 对账首次发现异常，等待下一轮确认: service={}, userId={}, playerId={}, gateSessionId={}, relation={}",
                        owner.getId(), entry.getUserId(), entry.getPlayerId(), entry.getGateSessionId(),
                        "Online");
                continue;
            }
            LogCore.core.warn("PlayerService 对账连续两轮发现异常，按原 Session 清理: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, relation={}",
                    owner.getId(), entry.getUserId(), entry.getPlayerId(), entry.getGate(), entry.getGateSessionId(),
                    "Online");
            offlineManager.onPlayerOffline(entry.getUserId(), entry.getPlayerId(),
                    entry.getGate(), entry.getGateSessionId(),
                    BrokenType.STATE_RECONCILE.getCode());
        }
    }

    private boolean containsSameSession(PlayerStateCheck[] entries, PlayerStateCheck expected) {
        for (PlayerStateCheck entry : entries) {
            if (entry.getUserId().equals(expected.getUserId())
                    && entry.getPlayerId() == expected.getPlayerId()
                    && entry.getGateSessionId() == expected.getGateSessionId()
                    && Objects.equals(entry.getGate(), expected.getGate())) {
                return true;
            }
        }
        return false;
    }

}
