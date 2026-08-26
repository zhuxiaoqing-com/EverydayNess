package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据PlayerOfflineRpcService生成的代理类
*/
public final class PlayerOfflineRpcProxy {

    private static final PlayerOfflineRpcProxy INSTANCE = new PlayerOfflineRpcProxy();

    private PlayerOfflineRpcProxy() {
    }

    public static PlayerOfflineRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_PLAYEROFFLINERPC_ONPLAYEROFFLINE_4 = 4;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendOnPlayerOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId, int brokenTypeCode){
        return RpcResult.run(() -> inst().onPlayerOffline(remote, userId, playerId, gate, gateSessionId, brokenTypeCode));
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.offline.PlayerOfflineRpc#onPlayerOffline()
    */
    public void onPlayerOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId, int brokenTypeCode){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYEROFFLINERPC_ONPLAYEROFFLINE_4, new Object[]{userId, playerId, gate, gateSessionId, brokenTypeCode});
    }


}
