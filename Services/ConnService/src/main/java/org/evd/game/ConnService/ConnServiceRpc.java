package org.evd.game.ConnService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.ActorType;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/** ConnService 客户端连接与负载查询 RPC 入口。 */
@Actor
@RpcHandler
public final class ConnServiceRpc {
    @Rpc
    public boolean pushToClient(long sessionId, ClientFrameChunk packet) {
        owner().pushToClient(sessionId, packet);
        return true;
    }

    @Rpc
    public void pushToUserId(String userId, ClientFrameChunk packet) {
        owner().pushToUserId(userId, packet);
    }

    @Rpc(actorType = ActorType.GATE)
    public void pushToPlayerId(long playerId, ClientFrameChunk packet) {
        owner().pushToPlayerId(playerId, packet);
    }

    @Rpc
    public void redirectClient(long sessionId, ClientFrameChunk packet) {
        owner().redirectClient(sessionId, packet);
    }

    @Rpc
    public String getPublicAddr() {
        return owner().getPublicAddr();
    }

    @Rpc
    public int getLoginSessionCount() {
        return owner().getLoginSessionCount();
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
