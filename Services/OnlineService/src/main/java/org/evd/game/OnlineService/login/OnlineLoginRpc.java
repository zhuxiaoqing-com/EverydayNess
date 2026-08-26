package org.evd.game.OnlineService.login;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
import org.evd.game.common.serializeBean.OnlineService.login.OnlineLoginAdmission;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** OnlineService 登录 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlineLoginRpc {
    @Rpc
    public OnlineLoginAdmission admitLogin(String userId, CallPoint requestGate, long requestSessionId) {
        return logic().admitLogin(userId, requestGate, requestSessionId);
    }

    @Rpc
    public void cancelQueuedLogin(String userId, CallPoint requestGate, long requestSessionId) {
        logic().cancelQueuedLogin(userId, requestGate, requestSessionId);
    }

    @Rpc
    public boolean cancelPendingSession(String userId, String token) {
        return logic().cancelPendingSession(userId, token);
    }

    private OnlineLoginLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineLoginLogic.class);
    }
}
