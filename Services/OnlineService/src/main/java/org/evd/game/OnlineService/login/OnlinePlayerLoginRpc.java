package org.evd.game.OnlineService.login;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
import org.evd.game.common.proto.C2S_SelectRoleEnter;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;

/** OnlineService 选角登录 RPC 入口。 */
@Actor
@RpcHandler
public final class OnlinePlayerLoginRpc {
    @Rpc
    public void selectRoleEnter(ClientSessionRef session, C2S_SelectRoleEnter request) {
        logic().selectRoleEnter(session, request);
    }

    private OnlinePlayerLoginLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlinePlayerLoginLogic.class);
    }
}
