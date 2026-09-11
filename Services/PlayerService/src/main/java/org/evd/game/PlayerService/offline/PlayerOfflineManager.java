package org.evd.game.PlayerService.offline;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.event.RoleLogoutEvent;
import org.evd.game.PlayerService.map.PlayerMapLogic;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.common.constant.MatchConst;
import org.evd.game.common.proxy.MatchService.MatchRpcProxy;
import org.evd.game.runtime.Service;
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
        if (!sessionManager.isCurrent(userId, playerId, gate, gateSessionId)) {
            LogCore.core.error("PlayerService 下线 Session 与当前绑定不一致: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenTypeCode={}",
                    owner.getId(), userId, playerId, gate, gateSessionId, brokenTypeCode);
            return;
        }

        LogCore.core.info("PlayerService 开始处理玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenTypeCode={}",
                owner.getId(), userId, playerId, gate, gateSessionId, brokenTypeCode);

        var matchCancelResult = MatchRpcProxy.callCancel(MatchConst.getMatchCallPoint(), playerId);
        if (!matchCancelResult.isSuccess()) {
            LogCore.core.warn("PlayerService 通知 MatchService 取消匹配失败: service={}, playerId={}, errorCode={}, message={}",
                    owner.getId(), playerId, matchCancelResult.getErrorCode(), matchCancelResult.getErrorMessage());
        }

        try {
            owner.getActor(PlayerMapLogic.class).leaveMap(playerId);
            Service.getCurrent().publishEvent(RoleLogoutEvent.Listener.class, new RoleLogoutEvent(playerId), RoleLogoutEvent.Listener::onEvent);
        } catch (Exception e) {
            LogCore.core.error("PlayerService 玩家离线地图或事件清理失败: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenTypeCode={}",
                    owner.getId(), userId, playerId, gate, gateSessionId, brokenTypeCode, e);
        }

        // 先标记 MDB 下线，后续 Actor/Location 清理可能等待 RPC，不能延迟 flush 计时。
        owner.getMdb().playerLogout(playerId);
        owner.removePlayerActorState(playerId);
        sessionManager.remove(playerId);

        LogCore.core.info("PlayerService 结束处理玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenType={}",
                owner.getId(), userId, playerId, gate, gateSessionId,
                BrokenType.fromCode(brokenTypeCode));
    }
}
