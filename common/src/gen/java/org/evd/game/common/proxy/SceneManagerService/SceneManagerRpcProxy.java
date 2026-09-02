package org.evd.game.common.proxy.SceneManagerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.runtime.call.CallPoint;

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
        public final static int ENUM_SCENEMANAGERRPC_ENTERMAP_0 = 0;
        public final static int ENUM_SCENEMANAGERRPC_GETSCENESTAGE_1 = 1;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendEnterMap(CallPoint remote, SMapEnterRequest request){
        return RpcResult.run(() -> inst().enterMap(remote, request));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<CallPoint> callGetSceneStage(CallPoint remote, long sceneId){
        return RpcResult.call(() -> inst().getSceneStage(remote, sceneId));
    }



    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#enterMap()
    */
    public void enterMap(CallPoint remote, SMapEnterRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        service.call(remote, EnumCall.ENUM_SCENEMANAGERRPC_ENTERMAP_0, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#getSceneStage()
    */
    public CallPoint getSceneStage(CallPoint remote, long sceneId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (CallPoint)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_GETSCENESTAGE_1, new Object[]{sceneId});
    }


}
