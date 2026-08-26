package org.evd.game.ConnService.offline;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.Service;

/** ConnService 离线 RPC 入口。 */
@Actor
@RpcHandler
public final class ConnOfflineRpc {
    @Rpc
    public void kickSession(long sessionId, int brokenTypeCode, String reason) {
        logic().kickSession(sessionId, brokenTypeCode, reason);
    }

    @Rpc
    public void closeSession(long sessionId, int brokenTypeCode, String reason) {
        logic().closeSession(sessionId, brokenTypeCode, reason);
    }

    private ConnOfflineLogic logic() {
        return Service.getCurrent(ConnService.class).getActor(ConnOfflineLogic.class);
    }
}
