package org.evd.game.PlayerService.offline;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.support.LogCore;

/** PlayerService 离线阶段的会话校验、Actor 注销和 Location 清理。 */
public final class PlayerOfflineManager {
    private final PlayerService owner;
    private final PlayerSessionManager sessionManager;

    public PlayerOfflineManager(PlayerService owner, PlayerSessionManager sessionManager) {
        this.owner = owner;
        this.sessionManager = sessionManager;
    }

    /** 只清理仍匹配当前网关会话的玩家，旧离线通知不会误删新绑定。 */
    public void onPlayerOffline(String userId, long playerId, CallPoint gate,
                                long gateSessionId, int brokenTypeCode) {
        if (!sessionManager.removeIfCurrent(userId, playerId, gate, gateSessionId)) {
            LogCore.core.info("PlayerService 忽略旧 Session 下线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    owner.getId(), userId, playerId, gate, gateSessionId);
            return;
        }

        owner.removePlayerActorState(playerId);
        LogCore.core.info("PlayerService 玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenType={}",
                owner.getId(), userId, playerId, gate, gateSessionId,
                BrokenType.fromCode(brokenTypeCode));
    }
}
