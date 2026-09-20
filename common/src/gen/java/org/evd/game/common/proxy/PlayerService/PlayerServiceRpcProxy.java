package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据PlayerServiceRpcService生成的代理类
*/
public final class PlayerServiceRpcProxy {

    private static final PlayerServiceRpcProxy INSTANCE = new PlayerServiceRpcProxy();

    private PlayerServiceRpcProxy() {
    }

    public static PlayerServiceRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_PLAYERSERVICERPC_GETONLINECOUNT_0 = 0;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Integer> callGetOnlineCount(CallPoint remote){
        return RpcResult.call(() -> inst().getOnlineCount(remote));
    }



    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerServiceRpc#getOnlineCount()
    */
    public int getOnlineCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICERPC_GETONLINECOUNT_0, new Object[]{});
    }


}
