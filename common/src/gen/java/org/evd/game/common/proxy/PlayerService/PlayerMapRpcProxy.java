package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;

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
        public final static int ENUM_PLAYERMAPRPC_READYENTERMAP_5 = 5;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendReadyEnterMap(CallPoint remote, long playerId, long transferId, SMapInfo targetInfo){
        return RpcResult.run(() -> inst().readyEnterMap(remote, playerId, transferId, targetInfo));
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.map.PlayerMapRpc#readyEnterMap()
    */
    public void readyEnterMap(CallPoint remote, long playerId, long transferId, SMapInfo targetInfo){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERMAPRPC_READYENTERMAP_5, new Object[]{playerId, transferId, targetInfo});
    }


}
