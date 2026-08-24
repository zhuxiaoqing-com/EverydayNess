package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.OnlineService.session.OnlineUserState;

/**
* 根据OnlineSessionActorService生成的代理类
*/
public final class OnlineSessionActorProxy {

    private static final OnlineSessionActorProxy INSTANCE = new OnlineSessionActorProxy();

    private OnlineSessionActorProxy() {
    }

    public static OnlineSessionActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINESESSIONACTOR_CLEARPLAYERSERVICE_9 = 9;
        public final static int ENUM_ONLINESESSIONACTOR_CLEARSESSION_10 = 10;
        public final static int ENUM_ONLINESESSIONACTOR_GETUSERSTATE_11 = 11;
        public final static int ENUM_ONLINESESSIONACTOR_ISPLAYEROFFLINE_12 = 12;
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
    public static RpcResult<OnlineUserState> callGetUserState(CallPoint remote, String userId){
        return RpcResult.call(() -> inst().getUserState(remote, userId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callIsPlayerOffline(CallPoint remote, String userId){
        return RpcResult.call(() -> inst().isPlayerOffline(remote, userId));
    }



    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionActor#clearPlayerService()
    */
    public boolean clearPlayerService(CallPoint remote, String userId, CallPoint gate, long gateSessionId, CallPoint expectedPlayerService){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_CLEARPLAYERSERVICE_9, new Object[]{userId, gate, gateSessionId, expectedPlayerService});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionActor#clearSession()
    */
    public CallPoint clearSession(CallPoint remote, String userId, CallPoint gate, long sessionId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (CallPoint)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_CLEARSESSION_10, new Object[]{userId, gate, sessionId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionActor#getUserState()
    */
    public OnlineUserState getUserState(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (OnlineUserState)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_GETUSERSTATE_11, new Object[]{userId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionActor#isPlayerOffline()
    */
    public boolean isPlayerOffline(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_ISPLAYEROFFLINE_12, new Object[]{userId});
    }


}
