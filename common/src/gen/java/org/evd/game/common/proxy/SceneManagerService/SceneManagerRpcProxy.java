package org.evd.game.common.proxy.SceneManagerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.constant.MapConst.SceneEnterResult;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import java.util.List;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;

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
        public final static int ENUM_SCENEMANAGERRPC_DESTROYSCENE_1 = 1;
        public final static int ENUM_SCENEMANAGERRPC_ENTERMAP_2 = 2;
        public final static int ENUM_SCENEMANAGERRPC_ENTERSCENE_3 = 3;
        public final static int ENUM_SCENEMANAGERRPC_EXITMAP_4 = 4;
        public final static int ENUM_SCENEMANAGERRPC_GETRUNNINGMAPS_5 = 5;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<SMapInfo> callCreateScene(CallPoint remote, SMapCreateRequest request){
        return RpcResult.call(() -> inst().createScene(remote, request));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendDestroyScene(CallPoint remote, SMapInfo mapInfo, CallPoint stage){
        return RpcResult.run(() -> inst().destroyScene(remote, mapInfo, stage));
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
    public static RpcResult<SceneEnterResult> callEnterScene(CallPoint remote, long sceneId, SPlayerMapData playerData){
        return RpcResult.call(() -> inst().enterScene(remote, sceneId, playerData));
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
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#createScene()
    */
    public SMapInfo createScene(CallPoint remote, SMapCreateRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (SMapInfo)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_CREATESCENE_0, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#destroyScene()
    */
    public void destroyScene(CallPoint remote, SMapInfo mapInfo, CallPoint stage){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        service.call(remote, EnumCall.ENUM_SCENEMANAGERRPC_DESTROYSCENE_1, new Object[]{mapInfo, stage});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#enterMap()
    */
    public boolean enterMap(CallPoint remote, PlayerEnterRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_ENTERMAP_2, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#enterScene()
    */
    public SceneEnterResult enterScene(CallPoint remote, long sceneId, SPlayerMapData playerData){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (SceneEnterResult)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_ENTERSCENE_3, new Object[]{sceneId, playerData});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#exitMap()
    */
    public boolean exitMap(CallPoint remote, SMapInfo mapInfo, long playerId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_EXITMAP_4, new Object[]{mapInfo, playerId});
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.SceneManagerRpc#getRunningMaps()
    */
    @SuppressWarnings("unchecked")
    public List<SRunningMapInfo> getRunningMaps(CallPoint remote, int mapCfgId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        return (List<SRunningMapInfo>)service.callWait(remote, EnumCall.ENUM_SCENEMANAGERRPC_GETRUNNINGMAPS_5, new Object[]{mapCfgId});
    }


}
