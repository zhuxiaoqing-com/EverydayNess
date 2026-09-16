package org.evd.game.ConnService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.ActorType;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/** ConnService 客户端连接与负载查询 RPC 入口。 */
@Actor
@RpcHandler
public final class ConnServiceRpc {
    @Rpc
    public boolean pushToClient(long sessionId, ClientFrameChunk packet) {
        return owner().clientConnection().pushToClient(sessionId, packet);
    }

    @Rpc
    public void pushToUserId(String userId, ClientFrameChunk packet) {
        owner().clientConnection().pushToUserId(userId, packet);
    }

    @Rpc(actorType = ActorType.GATE)
    public void pushToPlayerId(ActorId actorId, ClientFrameChunk packet) {
        owner().clientConnection().pushToPlayerId(actorId.getUniqueId(), packet);
    }

    @Rpc
    public void redirectClient(long sessionId, ClientFrameChunk packet) {
        owner().clientConnection().redirectClient(sessionId, packet);
    }

    @Rpc
    public String getPublicAddr() {
        return owner().getPublicAddr();
    }

    @Rpc
    public int getLoginSessionCount() {
        return owner().clientConnection().getLoginSessionCount();
    }

    @Rpc
    public boolean cacheStageActorAddress(long playerId, ActorAddress stageActorAddress) {
        return owner().playerActorAddressRegistry().cacheStageActorAddress(playerId, stageActorAddress);
    }

    @Rpc
    public void removeStageActorAddress(long playerId) {
        owner().playerActorAddressRegistry().removeStageActorAddress(playerId);
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
