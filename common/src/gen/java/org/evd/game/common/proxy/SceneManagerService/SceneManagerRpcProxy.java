package org.evd.game.common.proxy.SceneManagerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MapRoute;

/**
* 根据SceneManagerRpcService生成的代理类
*/
public final class SceneManagerRpcProxy {

    private static final SceneManagerRpcProxy INSTANCE = new SceneManagerRpcProxy();

    private SceneManagerRpcProxy() {
    }

    public static SceneManagerRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_SCENEMANAGERRPC_ACQUIREMAP_0 = 0;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<MapRoute> callAcquireMap(CallPoint remote, int mapConfigId){
        return RpcResult.call(() -> inst().acquireMap(remote, mapConfigId));
    }



    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#acquireMap()
    */
    public MapRoute acquireMap(CallPoint remote, int mapConfigId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (MapRoute)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_ACQUIREMAP_0, new Object[]{mapConfigId});
    }


}
