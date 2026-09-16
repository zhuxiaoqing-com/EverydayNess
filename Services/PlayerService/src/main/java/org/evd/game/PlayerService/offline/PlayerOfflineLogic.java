package org.evd.game.PlayerService.offline;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.event.RoleLogoutEvent;
import org.evd.game.PlayerService.map.PlayerMapLogic;
import org.evd.game.PlayerService.map.match.PlayerMatchLogic;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.proxy.ConnService.ConnOfflineRpcProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

/** PlayerService 的玩家离线业务逻辑。 */
@Actor
public final class PlayerOfflineLogic {
    /** 校验当前网关会话后注销玩家 Actor 和 Location。 */
    public void onPlayerOffline(String userId, long playerId, CallPoint gate,
                                long gateSessionId, int brokenTypeCode) {
        PlayerService owner = owner();
        PlayerSessionManager sessionManager = owner.sessionManager();
        if (!sessionManager.isCurrent(userId, playerId, gate, gateSessionId)) {
            LogCore.core.error("PlayerService 下线 Session 与当前绑定不一致: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenTypeCode={}",
                    owner.getId(), userId, playerId, gate, gateSessionId, brokenTypeCode);
            return;
        }

        LogCore.core.info("PlayerService 开始处理玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenTypeCode={}",
                owner.getId(), userId, playerId, gate, gateSessionId, brokenTypeCode);

        try {
            Service.getCurrent().publishEvent(RoleLogoutEvent.Listener.class, new RoleLogoutEvent(playerId), RoleLogoutEvent.Listener::onEvent);
        } catch (Exception e) {
            LogCore.core.error("PlayerService 玩家离线事件清理失败: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenTypeCode={}",
                    owner.getId(), userId, playerId, gate, gateSessionId, brokenTypeCode, e);
        }



        // 先标记 MDB 下线，后续 Actor/Location 清理可能等待 RPC，不能延迟 flush 计时。
        try {
            owner.getActor(PlayerMapLogic.class).leaveMap(playerId);
        } catch (Exception e) {
            LogCore.core.error("PlayerService 玩家离线离开地图失败: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId, e);
        }
        try {
            owner.getActor(PlayerMatchLogic.class).cancelOnOffline(playerId);
        } catch (Exception e) {
            LogCore.core.error("PlayerService 玩家离线取消匹配失败: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId, e);
        }
        try {
            owner.getMdb().playerLogout(playerId);
        } catch (Exception e) {
            LogCore.core.error("PlayerService 玩家离线标记 MDB 下线失败: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId, e);
        }
        try {
            owner.removePlayerActorState(playerId);
        } catch (Exception e) {
            LogCore.core.error("PlayerService 玩家离线删除 Actor 状态失败: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId, e);
        }

        sessionManager.remove(playerId);

        LogCore.core.info("PlayerService 结束处理玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenType={}",
                owner.getId(), userId, playerId, gate, gateSessionId,
                BrokenType.fromCode(brokenTypeCode));
    }

    /** 关闭玩家当前网关会话，触发统一的离线清理流程。 */
    public void kickPlayer(long playerId, String reason) {
        PPlayerOnline online = owner().sessionManager().get(playerId);
        if (online == null || online.getGate() == null || online.getGateSessionId() <= 0L) {
            LogCore.core.warn("PlayerService 踢出玩家时找不到玩家会话: playerId={}, reason={}", playerId, reason);
            return;
        }
        RpcResult<Void> result = ConnOfflineRpcProxy.sendCloseSession(
                online.getGate(), online.getGateSessionId(),
                BrokenType.SERVER_KICK.getCode(), reason);
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 踢出玩家失败: playerId={}, gate={}, gateSessionId={}, reason={}, errorCode={}, message={}",
                    playerId, online.getGate(), online.getGateSessionId(), reason,
                    result.getErrorCode(), result.getErrorMessage());
        }
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }
}
