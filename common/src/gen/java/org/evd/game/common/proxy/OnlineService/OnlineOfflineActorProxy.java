package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据OnlineOfflineActorService生成的代理类
*/
public final class OnlineOfflineActorProxy {

    private static final OnlineOfflineActorProxy INSTANCE = new OnlineOfflineActorProxy();

    private OnlineOfflineActorProxy() {
    }

    public static OnlineOfflineActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINEOFFLINEACTOR_ONSESSIONOFFLINE_4 = 4;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendOnSessionOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId, int brokenTypeCode){
        return RpcResult.run(() -> inst().onSessionOffline(remote, userId, playerId, gate, gateSessionId, brokenTypeCode));
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.offline.OnlineOfflineActor#onSessionOffline()
    */
    public void onSessionOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId, int brokenTypeCode){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        service.call(remote, EnumCall.ENUM_ONLINEOFFLINEACTOR_ONSESSIONOFFLINE_4, new Object[]{userId, playerId, gate, gateSessionId, brokenTypeCode});
    }


}
