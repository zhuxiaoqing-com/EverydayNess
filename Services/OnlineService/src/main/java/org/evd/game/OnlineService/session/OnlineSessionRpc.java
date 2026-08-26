package org.evd.game.OnlineService.session;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.OnlineService.session.OnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** OnlineService 在线会话 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlineSessionRpc {
    @Rpc
    public OnlineUserState getUserState(String userId) {
        return logic().getUserState(userId);
    }

    @Rpc
    public boolean isPlayerOffline(String userId) {
        return logic().isPlayerOffline(userId);
    }

    @Rpc
    public CallPoint clearSession(String userId, CallPoint gate, long sessionId) {
        return logic().clearSession(userId, gate, sessionId);
    }

    @Rpc
    public boolean clearPlayerService(String userId, CallPoint gate, long gateSessionId,
                                      CallPoint expectedPlayerService) {
        return logic().clearPlayerService(userId, gate, gateSessionId, expectedPlayerService);
    }

    private OnlineSessionLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineSessionLogic.class);
    }
}
