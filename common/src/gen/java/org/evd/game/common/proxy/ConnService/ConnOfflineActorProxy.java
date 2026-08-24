package org.evd.game.common.proxy.ConnService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据ConnOfflineActorService生成的代理类
*/
public final class ConnOfflineActorProxy {

    private static final ConnOfflineActorProxy INSTANCE = new ConnOfflineActorProxy();

    private ConnOfflineActorProxy() {
    }

    public static ConnOfflineActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNOFFLINEACTOR_CLOSESESSION_13 = 13;
        public final static int ENUM_CONNOFFLINEACTOR_KICKSESSION_14 = 14;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendCloseSession(CallPoint remote, long sessionId, int brokenTypeCode, String reason){
        return RpcResult.run(() -> inst().closeSession(remote, sessionId, brokenTypeCode, reason));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendKickSession(CallPoint remote, long sessionId, int brokenTypeCode, String reason){
        return RpcResult.run(() -> inst().kickSession(remote, sessionId, brokenTypeCode, reason));
    }


    /**
    * 对应源方法: org.evd.game.ConnService.offline.ConnOfflineActor#closeSession()
    */
    public void closeSession(CallPoint remote, long sessionId, int brokenTypeCode, String reason){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNOFFLINEACTOR_CLOSESESSION_13, new Object[]{sessionId, brokenTypeCode, reason});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.offline.ConnOfflineActor#kickSession()
    */
    public void kickSession(CallPoint remote, long sessionId, int brokenTypeCode, String reason){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNOFFLINEACTOR_KICKSESSION_14, new Object[]{sessionId, brokenTypeCode, reason});
    }


}
