package org.evd.game.PlayerService.map;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.ActorType;
import org.evd.game.annotation.actor.ClientCmd;
import org.evd.game.annotation.actor.ClientCmdHandler;
import org.evd.game.common.proto.C2S_ReadyEnterMap;
import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;

/** 玩家客户端完成地图加载后的命令入口。 */
@Actor
@ClientCmdHandler
public final class PlayerMapClientCmd {
    @ClientCmd(value = MsgId.C2S_READY_ENTER_MAP_VALUE, actorType = ActorType.PLAYER)
    public void readyEnterMap(ClientSessionRef session, C2S_ReadyEnterMap request) {
        Service.getCurrent(PlayerService.class).getActor(PlayerMapLogic.class)
                .confirmReadyEnterMap(session, request);
    }
}
