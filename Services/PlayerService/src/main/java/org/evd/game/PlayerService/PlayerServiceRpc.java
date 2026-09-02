package org.evd.game.PlayerService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.Service;

import java.util.List;

/** PlayerService 负载查询 RPC 入口。 */
@Actor
@RpcHandler
public final class PlayerServiceRpc {
    @Rpc
    public int getOnlineCount() {
        return Service.getCurrent(PlayerService.class).getOnlineCount();
    }

    @Rpc
    public List<String> getMdbPlayerUserIds() {
        return Service.getCurrent(PlayerService.class).getMdbPlayerUserIds();
    }
}
