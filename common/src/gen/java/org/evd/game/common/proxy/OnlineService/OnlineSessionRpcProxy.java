package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.OnlineService.session.SOnlineUserState;

/**
* 根据OnlineSessionRpcService生成的代理类
*/
public final class OnlineSessionRpcProxy {

    private static final OnlineSessionRpcProxy INSTANCE = new OnlineSessionRpcProxy();

    private OnlineSessionRpcProxy() {
    }

    public static OnlineSessionRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINESESSIONRPC_CACHESTAGEACTORADDRESS_9 = 9;
        public final static int ENUM_ONLINESESSIONRPC_CLEARPLAYERSERVICE_10 = 10;
        public final static int ENUM_ONLINESESSIONRPC_CLEARSESSION_11 = 11;
        public final static int ENUM_ONLINESESSIONRPC_GETUSERSTATE_12 = 12;
        public final static int ENUM_ONLINESESSIONRPC_ISPLAYEROFFLINE_13 = 13;
        public final static int ENUM_ONLINESESSIONRPC_REMOVEHISTORICALPLAYERSERVICE_14 = 14;
        public final static int ENUM_ONLINESESSIONRPC_REMOVESTAGEACTORADDRESS_15 = 15;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callCacheStageActorAddress(CallPoint remote, long playerId, ActorAddress stageActorAddress){
        return RpcResult.call(() -> inst().cacheStageActorAddress(remote, playerId, stageActorAddress));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callClearPlayerService(CallPoint remote, String userId, CallPoint gate, long gateSessionId, CallPoint expectedPlayerService){
        return RpcResult.call(() -> inst().clearPlayerService(remote, userId, gate, gateSessionId, expectedPlayerService));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<CallPoint> callClearSession(CallPoint remote, String userId, CallPoint gate, long sessionId){
        return RpcResult.call(() -> inst().clearSession(remote, userId, gate, sessionId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<SOnlineUserState> callGetUserState(CallPoint remote, String userId){
        return RpcResult.call(() -> inst().getUserState(remote, userId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callIsPlayerOffline(CallPoint remote, String userId){
        return RpcResult.call(() -> inst().isPlayerOffline(remote, userId));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRemoveHistoricalPlayerService(CallPoint remote, String userId, CallPoint expectedPlayerService){
        return RpcResult.run(() -> inst().removeHistoricalPlayerService(remote, userId, expectedPlayerService));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRemoveStageActorAddress(CallPoint remote, long playerId){
        return RpcResult.run(() -> inst().removeStageActorAddress(remote, playerId));
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionRpc#cacheStageActorAddress()
    */
    public boolean cacheStageActorAddress(CallPoint remote, long playerId, ActorAddress stageActorAddress){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONRPC_CACHESTAGEACTORADDRESS_9, new Object[]{playerId, stageActorAddress});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionRpc#clearPlayerService()
    */
    public boolean clearPlayerService(CallPoint remote, String userId, CallPoint gate, long gateSessionId, CallPoint expectedPlayerService){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONRPC_CLEARPLAYERSERVICE_10, new Object[]{userId, gate, gateSessionId, expectedPlayerService});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionRpc#clearSession()
    */
    public CallPoint clearSession(CallPoint remote, String userId, CallPoint gate, long sessionId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (CallPoint)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONRPC_CLEARSESSION_11, new Object[]{userId, gate, sessionId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionRpc#getUserState()
    */
    public SOnlineUserState getUserState(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (SOnlineUserState)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONRPC_GETUSERSTATE_12, new Object[]{userId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionRpc#isPlayerOffline()
    */
    public boolean isPlayerOffline(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONRPC_ISPLAYEROFFLINE_13, new Object[]{userId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionRpc#removeHistoricalPlayerService()
    */
    public void removeHistoricalPlayerService(CallPoint remote, String userId, CallPoint expectedPlayerService){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        service.call(remote, EnumCall.ENUM_ONLINESESSIONRPC_REMOVEHISTORICALPLAYERSERVICE_14, new Object[]{userId, expectedPlayerService});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionRpc#removeStageActorAddress()
    */
    public void removeStageActorAddress(CallPoint remote, long playerId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        service.call(remote, EnumCall.ENUM_ONLINESESSIONRPC_REMOVESTAGEACTORADDRESS_15, new Object[]{playerId});
    }


}
