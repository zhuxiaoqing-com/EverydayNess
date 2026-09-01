package org.evd.game.common.proxy.StageService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;

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
        public final static int ENUM_STAGESERVICERPC_CREATEMAP_4 = 4;
        public final static int ENUM_STAGESERVICERPC_DESTROYMAP_5 = 5;
        public final static int ENUM_STAGESERVICERPC_ENTERMAP_6 = 6;
        public final static int ENUM_STAGESERVICERPC_LEAVEMAP_7 = 7;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callCreateMap(CallPoint remote, long mapInstanceId, int mapConfigId){
        return RpcResult.call(() -> inst().createMap(remote, mapInstanceId, mapConfigId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callDestroyMap(CallPoint remote, long mapInstanceId){
        return RpcResult.call(() -> inst().destroyMap(remote, mapInstanceId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callEnterMap(CallPoint remote, long mapInstanceId, long playerId, long enterSeq){
        return RpcResult.call(() -> inst().enterMap(remote, mapInstanceId, playerId, enterSeq));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callLeaveMap(CallPoint remote, long mapInstanceId, long playerId, long enterSeq){
        return RpcResult.call(() -> inst().leaveMap(remote, mapInstanceId, playerId, enterSeq));
    }



    /**
    * 对应源方法: org.evd.game.StageService.StageServiceRpc#createMap()
    */
    public boolean createMap(CallPoint remote, long mapInstanceId, int mapConfigId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_CREATEMAP_4, new Object[]{mapInstanceId, mapConfigId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.StageServiceRpc#destroyMap()
    */
    public boolean destroyMap(CallPoint remote, long mapInstanceId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_DESTROYMAP_5, new Object[]{mapInstanceId});
    }


    /**
    * 对应源方法: org.evd.game.StageService.StageServiceRpc#enterMap()
    */
    public boolean enterMap(CallPoint remote, long mapInstanceId, long playerId, long enterSeq){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_ENTERMAP_6, new Object[]{mapInstanceId, playerId, enterSeq});
    }


    /**
    * 对应源方法: org.evd.game.StageService.StageServiceRpc#leaveMap()
    */
    public boolean leaveMap(CallPoint remote, long mapInstanceId, long playerId, long enterSeq){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_STAGESERVICERPC_LEAVEMAP_7, new Object[]{mapInstanceId, playerId, enterSeq});
    }


}
