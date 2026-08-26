package org.evd.game.PlayerService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
import org.evd.game.runtime.Service;

/** PlayerService 负载查询 RPC 入口。 */
@Actor
@RpcHandler
public final class PlayerServiceRpc {
    @Rpc
    public int getOnlineCount() {
        return Service.getCurrent(PlayerService.class).getOnlineCount();
    }
}
