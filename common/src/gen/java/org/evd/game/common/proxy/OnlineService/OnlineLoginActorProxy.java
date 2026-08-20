package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.OnlineService.OnlineLoginAdmission;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据OnlineLoginActorService生成的代理类
*/
public final class OnlineLoginActorProxy {

    private static final OnlineLoginActorProxy INSTANCE = new OnlineLoginActorProxy();

    private OnlineLoginActorProxy() {
    }

    public static OnlineLoginActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINELOGINACTOR_ADMITLOGIN_0 = 0;
        public final static int ENUM_ONLINELOGINACTOR_CANCELPENDINGSESSION_1 = 1;
        public final static int ENUM_ONLINELOGINACTOR_CANCELQUEUEDLOGIN_2 = 2;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<OnlineLoginAdmission> callAdmitLogin(CallPoint remote, String userId, CallPoint requestGate, long requestSessionId){
        return RpcResult.call(() -> inst().admitLogin(remote, userId, requestGate, requestSessionId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callCancelPendingSession(CallPoint remote, String userId, String token){
        return RpcResult.call(() -> inst().cancelPendingSession(remote, userId, token));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendCancelQueuedLogin(CallPoint remote, String userId, CallPoint requestGate, long requestSessionId){
        return RpcResult.run(() -> inst().cancelQueuedLogin(remote, userId, requestGate, requestSessionId));
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.login.OnlineLoginActor#admitLogin()
    */
    public OnlineLoginAdmission admitLogin(CallPoint remote, String userId, CallPoint requestGate, long requestSessionId){
        Service service = Service.getCurrent();
        return (OnlineLoginAdmission)service.callWait(remote, EnumCall.ENUM_ONLINELOGINACTOR_ADMITLOGIN_0, new Object[]{userId, requestGate, requestSessionId});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.login.OnlineLoginActor#cancelPendingSession()
    */
    public boolean cancelPendingSession(CallPoint remote, String userId, String token){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_ONLINELOGINACTOR_CANCELPENDINGSESSION_1, new Object[]{userId, token});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.login.OnlineLoginActor#cancelQueuedLogin()
    */
    public void cancelQueuedLogin(CallPoint remote, String userId, CallPoint requestGate, long requestSessionId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_ONLINELOGINACTOR_CANCELQUEUEDLOGIN_2, new Object[]{userId, requestGate, requestSessionId});
    }


}
