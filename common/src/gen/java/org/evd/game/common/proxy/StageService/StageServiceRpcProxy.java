package org.evd.game.common.proxy.StageService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;

/**
* 根据StageServiceRpcService生成的代理类
*/
public final class StageServiceRpcProxy {

    private static final StageServiceRpcProxy INSTANCE = new StageServiceRpcProxy();

    private StageServiceRpcProxy() {
    }

    public static StageServiceRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_STAGESERVICERPC_CREATESCENE_4 = 4;
        public final static int ENUM_STAGESERVICERPC_DESTROYSCENE_5 = 5;
        public final static int ENUM_STAGESERVICERPC_ENTERSCENE_6 = 6;
        public final static int ENUM_STAGESERVICERPC_EXITSCENE_7 = 7;
        public final static int ENUM_STAGESERVICERPC_GETMAPCOUNT_8 = 8;
        public final static int ENUM_STAGESERVICERPC_PREPAREENTERSCENE_9 = 9;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callCreateScene(CallPoint remote, SMapKey mapKey, long sceneId){
        return RpcResult.call(() -> inst().createScene(remote, mapKey, sceneId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callDestroyScene(CallPoint remote, long sceneId){
        return RpcResult.call(() -> inst().destroyScene(remote, sceneId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callEnterScene(CallPoint remote, long sceneId, SPlayerMapData playerData){
        return RpcResult.call(() -> inst().enterScene(remote, sceneId, playerData));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callExitScene(CallPoint remote, long sceneId, long playerId){
        return RpcResult.call(() -> inst().exitScene(remote, sceneId, playerId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Integer> callGetMapCount(CallPoint remote){
        return RpcResult.call(() -> inst().getMapCount(remote));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callPrepareEnterScene(CallPoint remote, SMapEnterRequest request){
        return RpcResult.call(() -> inst().prepareEnterScene(remote, request));
    }



    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#createScene()
    */
    public boolean createScene(CallPoint remote, SMapKey mapKey, long sceneId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_CREATESCENE_4, new Object[]{mapKey, sceneId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#destroyScene()
    */
    public boolean destroyScene(CallPoint remote, long sceneId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_DESTROYSCENE_5, new Object[]{sceneId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#enterScene()
    */
    public boolean enterScene(CallPoint remote, long sceneId, SPlayerMapData playerData){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_ENTERSCENE_6, new Object[]{sceneId, playerData});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#exitScene()
    */
    public boolean exitScene(CallPoint remote, long sceneId, long playerId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_EXITSCENE_7, new Object[]{sceneId, playerId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#getMapCount()
    */
    public int getMapCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_GETMAPCOUNT_8, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#prepareEnterScene()
    */
    public boolean prepareEnterScene(CallPoint remote, SMapEnterRequest request){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_PREPAREENTERSCENE_9, new Object[]{request});
    }


}
