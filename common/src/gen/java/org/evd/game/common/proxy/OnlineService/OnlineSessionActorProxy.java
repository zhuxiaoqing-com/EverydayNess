package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.OnlineService.OnlineUserState;

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
        public final static int ENUM_ONLINESESSIONACTOR_CLEARPLAYERSERVICE_7 = 7;
        public final static int ENUM_ONLINESESSIONACTOR_CLEARSESSION_8 = 8;
        public final static int ENUM_ONLINESESSIONACTOR_GETUSERSTATE_9 = 9;
        public final static int ENUM_ONLINESESSIONACTOR_ISPLAYEROFFLINE_10 = 10;
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
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_CLEARPLAYERSERVICE_7, new Object[]{userId, gate, gateSessionId, expectedPlayerService});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionActor#clearSession()
    */
    public CallPoint clearSession(CallPoint remote, String userId, CallPoint gate, long sessionId){
        Service service = Service.getCurrent();
        return (CallPoint)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_CLEARSESSION_8, new Object[]{userId, gate, sessionId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionActor#getUserState()
    */
    public OnlineUserState getUserState(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        return (OnlineUserState)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_GETUSERSTATE_9, new Object[]{userId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.session.OnlineSessionActor#isPlayerOffline()
    */
    public boolean isPlayerOffline(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINESESSIONACTOR_ISPLAYEROFFLINE_10, new Object[]{userId});
    }


}
