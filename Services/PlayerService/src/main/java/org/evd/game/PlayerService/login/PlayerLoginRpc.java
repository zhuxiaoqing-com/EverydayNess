package org.evd.game.PlayerService.login;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.proto.RoleData;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.client.ClientSessionRef;

/** PlayerService 玩家登录 RPC 入口。 */
@Actor
@RpcHandler
public final class PlayerLoginRpc {
    @Rpc
    public ActorAddress loginPlayer(String userId, RoleData role, ClientSessionRef session) {
        return logic().loginPlayer(userId, role, session);
    }

    @Rpc
    public void onlinePlayer(String userId, long playerId, RoleData role, ClientSessionRef session) {
        logic().onlinePlayer(userId, playerId, role, session);
    }

    @Rpc
    public void bindGateActorAddress(long playerId, ActorAddress gateActorAddress) {
        logic().bindGateActorAddress(playerId, gateActorAddress);
    }

    private PlayerLoginLogic logic() {
        return Service.getCurrent(PlayerService.class).getActor(PlayerLoginLogic.class);
    }
}
