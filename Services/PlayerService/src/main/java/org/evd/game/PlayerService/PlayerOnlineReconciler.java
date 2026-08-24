package org.evd.game.PlayerService;

import org.evd.game.PlayerService.offline.PlayerOfflineManager;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.common.proxy.OnlineService.OnlineStateReconcileActorProxy;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** PlayerService 的玩家运行态对账，负责清理 Online 已不存在的玩家实例。 */
public final class PlayerOnlineReconciler {
    public static final long INTERVAL_MILLIS = 30_000L;

    private final PlayerService owner;
    private final PlayerSessionManager sessionManager;
    private final PlayerOfflineManager offlineManager;

    public PlayerOnlineReconciler(PlayerService owner, PlayerSessionManager sessionManager,
                                  PlayerOfflineManager offlineManager) {
        this.owner = owner;
        this.sessionManager = sessionManager;
        this.offlineManager = offlineManager;
    }

    /** 将 PlayerService 当前运行态交给 Online 校验，并按原会话精确清理失效玩家。 */
    public void reconcile() {
        List<PlayerStateCheck> entries = new ArrayList<>();
        for (Map.Entry<Long, PPlayerOnline> bindingEntry
                : sessionManager.snapshotBindings().entrySet()) {
            PPlayerOnline binding = bindingEntry.getValue();
            entries.add(new PlayerStateCheck(
                    binding.getUserId(), bindingEntry.getKey(), binding.getGate(),
                    binding.getGateSessionId()));
        }
        if (entries.isEmpty()) {
            return;
        }

        RpcResult<PlayerStateCheck[]> result =
                OnlineStateReconcileActorProxy.callReconcilePlayerSessions(
                        null, owner.getCallPoint(), entries);
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 对账请求失败: service={}, target=OnlineService, count={}, errorCode={}, message={}",
                    owner.getId(), entries.size(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }

        PlayerStateCheck[] invalidEntries = result.getValue();
        if (invalidEntries.length == 0) {
            return;
        }
        for (PlayerStateCheck entry : invalidEntries) {
            LogCore.core.warn("PlayerService 对账发现残留玩家，按原 Session 清理: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    owner.getId(), entry.getUserId(), entry.getPlayerId(),
                    entry.getGate(), entry.getGateSessionId());
            offlineManager.onPlayerOffline(entry.getUserId(), entry.getPlayerId(), entry.getGate(),
                    entry.getGateSessionId(), BrokenType.STATE_RECONCILE.getCode());
        }
    }
}
