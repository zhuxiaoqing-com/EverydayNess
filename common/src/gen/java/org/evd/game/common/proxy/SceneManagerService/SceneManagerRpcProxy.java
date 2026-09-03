package org.evd.game.common.proxy.SceneManagerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
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
        public final static int ENUM_SCENEMANAGERRPC_EXITMAP_1 = 1;
        public final static int ENUM_SCENEMANAGERRPC_GETSCENESTAGE_2 = 2;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callEnterMap(CallPoint remote, SMapEnterRequest request){
        return RpcResult.call(() -> inst().enterMap(remote, request));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callExitMap(CallPoint remote, SMapInfo mapInfo, long playerId){
        return RpcResult.call(() -> inst().exitMap(remote, mapInfo, playerId));
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
    public boolean enterMap(CallPoint remote, SMapEnterRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_ENTERMAP_0, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#exitMap()
    */
    public boolean exitMap(CallPoint remote, SMapInfo mapInfo, long playerId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_EXITMAP_1, new Object[]{mapInfo, playerId});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#getSceneStage()
    */
    public CallPoint getSceneStage(CallPoint remote, long sceneId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (CallPoint)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_GETSCENESTAGE_2, new Object[]{sceneId});
    }


}
