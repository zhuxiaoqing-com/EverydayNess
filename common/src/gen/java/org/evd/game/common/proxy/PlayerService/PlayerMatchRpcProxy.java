package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MatchPlayerEnterMapParam;

/**
* 根据PlayerMatchRpcService生成的代理类
*/
public final class PlayerMatchRpcProxy {

    private static final PlayerMatchRpcProxy INSTANCE = new PlayerMatchRpcProxy();

    private PlayerMatchRpcProxy() {
    }

    public static PlayerMatchRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_PLAYERMATCHRPC_MATCHENTERMAP_9 = 9;
        public final static int ENUM_PLAYERMATCHRPC_ONMATCHRESULT_10 = 10;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendMatchEnterMap(CallPoint remote, long playerId, SMapInfo targetInfo, MatchPlayerEnterMapParam matchParam){
        return RpcResult.run(() -> inst().matchEnterMap(remote, playerId, targetInfo, matchParam));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendOnMatchResult(CallPoint remote, long playerId, boolean success, boolean isTeamMatch){
        return RpcResult.run(() -> inst().onMatchResult(remote, playerId, success, isTeamMatch));
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.match.PlayerMatchRpc#matchEnterMap()
    */
    public void matchEnterMap(CallPoint remote, long playerId, SMapInfo targetInfo, MatchPlayerEnterMapParam matchParam){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERMATCHRPC_MATCHENTERMAP_9, new Object[]{playerId, targetInfo, matchParam});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.match.PlayerMatchRpc#onMatchResult()
    */
    public void onMatchResult(CallPoint remote, long playerId, boolean success, boolean isTeamMatch){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERMATCHRPC_ONMATCHRESULT_10, new Object[]{playerId, success, isTeamMatch});
    }


}
