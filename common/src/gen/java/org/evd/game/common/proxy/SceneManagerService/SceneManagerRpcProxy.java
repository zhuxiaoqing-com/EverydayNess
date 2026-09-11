package org.evd.game.common.proxy.SceneManagerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import java.util.List;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
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
        public final static int ENUM_SCENEMANAGERRPC_CREATESCENE_0 = 0;
        public final static int ENUM_SCENEMANAGERRPC_ENTERMAP_1 = 1;
        public final static int ENUM_SCENEMANAGERRPC_EXITMAP_2 = 2;
        public final static int ENUM_SCENEMANAGERRPC_GETRUNNINGMAPS_3 = 3;
        public final static int ENUM_SCENEMANAGERRPC_GETSCENESTAGE_4 = 4;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<SMapInfo> callCreateScene(CallPoint remote, SMapCreateRequest request){
        return RpcResult.call(() -> inst().createScene(remote, request));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callEnterMap(CallPoint remote, PlayerEnterRequest request){
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
    public static RpcResult<List<SRunningMapInfo>> callGetRunningMaps(CallPoint remote, int mapCfgId){
        return RpcResult.call(() -> inst().getRunningMaps(remote, mapCfgId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<CallPoint> callGetSceneStage(CallPoint remote, long sceneId){
        return RpcResult.call(() -> inst().getSceneStage(remote, sceneId));
    }



    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#createScene()
    */
    public SMapInfo createScene(CallPoint remote, SMapCreateRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (SMapInfo)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_CREATESCENE_0, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#enterMap()
    */
    public boolean enterMap(CallPoint remote, PlayerEnterRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_ENTERMAP_1, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#exitMap()
    */
    public boolean exitMap(CallPoint remote, SMapInfo mapInfo, long playerId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_EXITMAP_2, new Object[]{mapInfo, playerId});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#getRunningMaps()
    */
    @SuppressWarnings("unchecked")
    public List<SRunningMapInfo> getRunningMaps(CallPoint remote, int mapCfgId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (List<SRunningMapInfo>)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_GETRUNNINGMAPS_3, new Object[]{mapCfgId});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#getSceneStage()
    */
    public CallPoint getSceneStage(CallPoint remote, long sceneId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (CallPoint)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_GETSCENESTAGE_4, new Object[]{sceneId});
    }


}
