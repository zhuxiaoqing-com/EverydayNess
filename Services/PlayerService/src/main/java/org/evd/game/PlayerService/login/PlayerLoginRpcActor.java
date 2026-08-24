package org.evd.game.PlayerService.login;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proto.RoleData;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.client.ClientSessionRef;

/** PlayerService 的玩家登录 RPC 入口。 */
@Actor
public final class PlayerLoginRpcActor {
    /** 创建玩家运行态并加载玩家数据。 */
    @Rpc
    public ActorAddress loginPlayer(String userId, RoleData role, ClientSessionRef session) {
        return owner().loginManager().loginPlayer(userId, role, session);
    }

    /** 完成 GW 玩家绑定并推进玩家进入地图。 */
    @Rpc
    public void onlinePlayer(String userId, long playerId, ClientSessionRef session,
                             ActorAddress gateActorAddress) {
        owner().loginManager().onlinePlayer(userId, playerId, session, gateActorAddress);
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }
}
