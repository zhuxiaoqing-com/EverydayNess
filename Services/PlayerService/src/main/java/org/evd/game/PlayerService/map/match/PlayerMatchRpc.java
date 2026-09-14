package org.evd.game.PlayerService.map.match;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MatchPlayerEnterMapParam;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;

/** MatchService 回调 PlayerService 的匹配结果、取消和进图 RPC 入口。 */
@Actor
@RpcHandler
public final class PlayerMatchRpc {
    @Rpc
    public void matchEnterMap(long playerId, SMapInfo targetInfo,
                              MatchPlayerEnterMapParam matchParam) {
        Service.getCurrent(PlayerService.class).getActor(PlayerMatchLogic.class)
                .matchEnterMap(playerId, targetInfo, matchParam);
    }

    /** 接收 MatchService 的匹配结果，由 PlayerService 转发给客户端。 */
    @Rpc
    public void onMatchResult(long playerId, boolean success, boolean isTeamMatch) {
        Service.getCurrent(PlayerService.class).getActor(PlayerMatchLogic.class)
                .onMatchResult(playerId, success, isTeamMatch);
    }
}
