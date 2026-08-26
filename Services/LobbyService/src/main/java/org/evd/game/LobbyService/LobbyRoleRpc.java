package org.evd.game.LobbyService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.proto.C2S_CreateRole;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;

/** Lobby 角色 RPC 入口。 */
@Actor
@RpcHandler
public final class LobbyRoleRpc {
    @Rpc
    public void createRole(ClientSessionRef session, C2S_CreateRole request) {
        logic().createRole(session, request);
    }

    @Rpc
    public void roleList(CallPoint gate, long gateSessionId, String userId) {
        logic().roleList(gate, gateSessionId, userId);
    }

    private LobbyRoleLogic logic() {
        return Service.getCurrent(LobbyService.class).getActor(LobbyRoleLogic.class);
    }
}
