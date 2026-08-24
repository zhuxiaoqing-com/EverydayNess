package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据PlayerServiceService生成的代理类
*/
public final class PlayerServiceProxy {

    private static final PlayerServiceProxy INSTANCE = new PlayerServiceProxy();

    private PlayerServiceProxy() {
    }

    public static PlayerServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_PLAYERSERVICE_GETONLINECOUNT_0 = 0;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Integer> callGetOnlineCount(CallPoint remote){
        return RpcResult.call(() -> inst().getOnlineCount(remote));
    }



    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#getOnlineCount()
    */
    public int getOnlineCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_GETONLINECOUNT_0, new Object[]{});
    }


}
