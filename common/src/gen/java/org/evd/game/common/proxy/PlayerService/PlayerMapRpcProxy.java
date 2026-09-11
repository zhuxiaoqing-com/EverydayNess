package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerEnterParam;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MatchPlayerEnterMapParam;
import org.evd.game.runtime.actor.ActorAddress;

/**
* 根据PlayerMapRpcService生成的代理类
*/
public final class PlayerMapRpcProxy {

    private static final PlayerMapRpcProxy INSTANCE = new PlayerMapRpcProxy();

    private PlayerMapRpcProxy() {
    }

    public static PlayerMapRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_PLAYERMAPRPC_CANCELMATCH_5 = 5;
        public final static int ENUM_PLAYERMAPRPC_CLEARMATCHSTATE_6 = 6;
        public final static int ENUM_PLAYERMAPRPC_ENTERMAP_7 = 7;
        public final static int ENUM_PLAYERMAPRPC_MATCHENTERMAP_8 = 8;
        public final static int ENUM_PLAYERMAPRPC_ONENTERMAP_9 = 9;
        public final static int ENUM_PLAYERMAPRPC_ONEXITMAP_10 = 10;
        public final static int ENUM_PLAYERMAPRPC_READYENTERMAP_11 = 11;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callCancelMatch(CallPoint remote, long playerId){
        return RpcResult.call(() -> inst().cancelMatch(remote, playerId));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendClearMatchState(CallPoint remote, long playerId){
        return RpcResult.run(() -> inst().clearMatchState(remote, playerId));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callEnterMap(CallPoint remote, long playerId, SMapInfo targetInfo, SPlayerEnterParam enterParam){
        return RpcResult.call(() -> inst().enterMap(remote, playerId, targetInfo, enterParam));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendMatchEnterMap(CallPoint remote, long playerId, SMapInfo targetInfo, MatchPlayerEnterMapParam matchParam){
        return RpcResult.run(() -> inst().matchEnterMap(remote, playerId, targetInfo, matchParam));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callOnEnterMap(CallPoint remote, long playerId, long transferId, SMapInfo targetInfo, ActorAddress stageActorAddress){
        return RpcResult.call(() -> inst().onEnterMap(remote, playerId, transferId, targetInfo, stageActorAddress));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendOnExitMap(CallPoint remote, long playerId, long sceneId, ActorAddress stageActorAddress){
        return RpcResult.run(() -> inst().onExitMap(remote, playerId, sceneId, stageActorAddress));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callReadyEnterMap(CallPoint remote, long playerId, long transferId, SMapInfo targetInfo){
        return RpcResult.call(() -> inst().readyEnterMap(remote, playerId, transferId, targetInfo));
    }



    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#cancelMatch()
    */
    public boolean cancelMatch(CallPoint remote, long playerId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_PLAYERMAPRPC_CANCELMATCH_5, new Object[]{playerId});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#clearMatchState()
    */
    public void clearMatchState(CallPoint remote, long playerId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERMAPRPC_CLEARMATCHSTATE_6, new Object[]{playerId});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#enterMap()
    */
    public boolean enterMap(CallPoint remote, long playerId, SMapInfo targetInfo, SPlayerEnterParam enterParam){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_PLAYERMAPRPC_ENTERMAP_7, new Object[]{playerId, targetInfo, enterParam});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#matchEnterMap()
    */
    public void matchEnterMap(CallPoint remote, long playerId, SMapInfo targetInfo, MatchPlayerEnterMapParam matchParam){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERMAPRPC_MATCHENTERMAP_8, new Object[]{playerId, targetInfo, matchParam});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#onEnterMap()
    */
    public boolean onEnterMap(CallPoint remote, long playerId, long transferId, SMapInfo targetInfo, ActorAddress stageActorAddress){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_PLAYERMAPRPC_ONENTERMAP_9, new Object[]{playerId, transferId, targetInfo, stageActorAddress});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#onExitMap()
    */
    public void onExitMap(CallPoint remote, long playerId, long sceneId, ActorAddress stageActorAddress){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERMAPRPC_ONEXITMAP_10, new Object[]{playerId, sceneId, stageActorAddress});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#readyEnterMap()
    */
    public boolean readyEnterMap(CallPoint remote, long playerId, long transferId, SMapInfo targetInfo){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_PLAYERMAPRPC_READYENTERMAP_11, new Object[]{playerId, transferId, targetInfo});
    }


}
