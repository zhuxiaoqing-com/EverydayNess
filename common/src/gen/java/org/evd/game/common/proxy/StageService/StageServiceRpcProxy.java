package org.evd.game.common.proxy.StageService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;

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
        public final static int ENUM_STAGESERVICERPC_ADDBUFF_4 = 4;
        public final static int ENUM_STAGESERVICERPC_CREATESCENE_5 = 5;
        public final static int ENUM_STAGESERVICERPC_CREATESCENE_6 = 6;
        public final static int ENUM_STAGESERVICERPC_DESTROYSCENE_7 = 7;
        public final static int ENUM_STAGESERVICERPC_ENTERSCENE_8 = 8;
        public final static int ENUM_STAGESERVICERPC_EXITSCENE_9 = 9;
        public final static int ENUM_STAGESERVICERPC_GETMAPCOUNT_10 = 10;
        public final static int ENUM_STAGESERVICERPC_GETRUNNINGMAPINFO_11 = 11;
        public final static int ENUM_STAGESERVICERPC_PREPAREENTERSCENE_12 = 12;
        public final static int ENUM_STAGESERVICERPC_SPAWNMONSTER_13 = 13;
        public final static int ENUM_STAGESERVICERPC_USESKILL_14 = 14;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callAddBuff(CallPoint remote, long sceneId, long targetId, int buffId){
        return RpcResult.call(() -> inst().addBuff(remote, sceneId, targetId, buffId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callCreateScene(CallPoint remote, SMapCreateRequest request, long sceneId){
        return RpcResult.call(() -> inst().createScene(remote, request, sceneId));
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
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendEnterScene(CallPoint remote, long sceneId, SPlayerMapData playerData){
        return RpcResult.run(() -> inst().enterScene(remote, sceneId, playerData));
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
    public static RpcResult<SRunningMapInfo> callGetRunningMapInfo(CallPoint remote, long sceneId){
        return RpcResult.call(() -> inst().getRunningMapInfo(remote, sceneId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callPrepareEnterScene(CallPoint remote, PlayerEnterRequest request){
        return RpcResult.call(() -> inst().prepareEnterScene(remote, request));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Long> callSpawnMonster(CallPoint remote, long sceneId, int monsterConfigId){
        return RpcResult.call(() -> inst().spawnMonster(remote, sceneId, monsterConfigId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callUseSkill(CallPoint remote, long sceneId, long casterId, int skillId, int level, long targetId){
        return RpcResult.call(() -> inst().useSkill(remote, sceneId, casterId, skillId, level, targetId));
    }



    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#addBuff()
    */
    public boolean addBuff(CallPoint remote, long sceneId, long targetId, int buffId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_ADDBUFF_4, new Object[]{sceneId, targetId, buffId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#createScene()
    */
    public boolean createScene(CallPoint remote, SMapCreateRequest request, long sceneId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_CREATESCENE_5, new Object[]{request, sceneId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#createScene()
    */
    public boolean createScene(CallPoint remote, SMapKey mapKey, long sceneId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_CREATESCENE_6, new Object[]{mapKey, sceneId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#destroyScene()
    */
    public boolean destroyScene(CallPoint remote, long sceneId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_DESTROYSCENE_7, new Object[]{sceneId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#enterScene()
    */
    public void enterScene(CallPoint remote, long sceneId, SPlayerMapData playerData){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICERPC_ENTERSCENE_8, new Object[]{sceneId, playerData});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#exitScene()
    */
    public boolean exitScene(CallPoint remote, long sceneId, long playerId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_EXITSCENE_9, new Object[]{sceneId, playerId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#getMapCount()
    */
    public int getMapCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_GETMAPCOUNT_10, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#getRunningMapInfo()
    */
    public SRunningMapInfo getRunningMapInfo(CallPoint remote, long sceneId){
        Service service = Service.getCurrent();
        return (SRunningMapInfo)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_GETRUNNINGMAPINFO_11, new Object[]{sceneId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#prepareEnterScene()
    */
    public boolean prepareEnterScene(CallPoint remote, PlayerEnterRequest request){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_PREPAREENTERSCENE_12, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#spawnMonster()
    */
    public long spawnMonster(CallPoint remote, long sceneId, int monsterConfigId){
        Service service = Service.getCurrent();
        return (long)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_SPAWNMONSTER_13, new Object[]{sceneId, monsterConfigId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.mapCreate.StageServiceRpc#useSkill()
    */
    public boolean useSkill(CallPoint remote, long sceneId, long casterId, int skillId, int level, long targetId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_USESKILL_14, new Object[]{sceneId, casterId, skillId, level, targetId});
    }


}
