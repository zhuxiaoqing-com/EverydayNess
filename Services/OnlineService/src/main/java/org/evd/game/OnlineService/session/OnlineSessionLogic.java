package org.evd.game.OnlineService.session;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.OnlineService.session.OnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.CoroutineLockTimeoutException;

/** OnlineService 在线会话与离线清理业务逻辑。 */
@Actor
public final class OnlineSessionLogic {
    /** 查询用户正式上线后的会话状态。 */
    public OnlineUserState getUserState(String userId) {
        return owner().sessionCoordinator().getUserState(userId);
    }

    /** 判断用户当前是否不存在正式在线会话。 */
    public boolean isPlayerOffline(String userId) {
        return owner().sessionCoordinator().isPlayerOffline(userId);
    }

    /** 删除已经由 PlayerService 确认过期的历史绑定。 */
    public void removeHistoricalPlayerService(String userId, CallPoint expectedPlayerService) {
        owner().sessionCoordinator().removeHistoricalPlayerService(userId, expectedPlayerService);
    }

    /** 清理匹配的正式在线会话，并返回其 PlayerService。 */
    public CallPoint clearSession(String userId, CallPoint gate, long sessionId) {
        return owner().sessionCoordinator().clearSession(userId, gate, sessionId);
    }

    /** 按网关会话和目标服务校验并清理 PlayerService 绑定。 */
    public boolean clearPlayerService(String userId, CallPoint gate, long gateSessionId,
                                      CallPoint expectedPlayerService) {
        try (ContinuationLockScope ignored = owner().awaitCoroutineLockScope(LockType.LOGIN, userId)) {
            return owner().sessionCoordinator().clearPlayerService(
                    userId, gate, gateSessionId, expectedPlayerService);
        } catch (CoroutineLockTimeoutException e) {
            LogCore.core.warn("OnlineService 玩家解绑协程锁等待超时: userId={}, gateSessionId={}, timeoutMillis={}",
                    userId, gateSessionId, e.getTimeoutMillis());
            return false;
        }
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
