package org.evd.game.LobbyService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.LobbyService.login.SLobbyUserAccessResult;
import org.evd.game.common.serializeBean.LobbyService.role.SLobbyRoleSnapshot;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** LobbyService 账号和角色状态 RPC 入口。 */
@Actor
@RpcHandler
public final class LobbyServiceRpc {
    @Rpc
    public SLobbyUserAccessResult validateOrCreateUser(String userId) {
        return owner().validateOrCreateUser(userId);
    }

    @Rpc
    public SLobbyRoleSnapshot getRole(String userId) {
        return owner().getRole(userId);
    }

    @Rpc
    public void playerOnline(String userId, long playerId, CallPoint gate, long gateSessionId) {
        owner().playerOnline(userId, playerId, gate, gateSessionId);
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }
}
