package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;

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
        public final static int ENUM_PLAYERSERVICE_BINDPLAYERSESSION_0 = 0;
        public final static int ENUM_PLAYERSERVICE_ENTERMAP_1 = 1;
        public final static int ENUM_PLAYERSERVICE_GETONLINECOUNT_2 = 2;
        public final static int ENUM_PLAYERSERVICE_ONPLAYEROFFLINE_3 = 3;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callBindPlayerSession(CallPoint remote, String userId, long playerId, ClientSessionRef session){
        return RpcResult.call(() -> inst().bindPlayerSession(remote, userId, playerId, session));
    }


    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callEnterMap(CallPoint remote, long playerId){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_ENTERMAP_1, new Object[]{playerId});
        });
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Integer> callGetOnlineCount(CallPoint remote){
        return RpcResult.call(() -> inst().getOnlineCount(remote));
    }


    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callOnPlayerOffline(CallPoint remote, String userId, long playerId, int brokenTypeCode){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_ONPLAYEROFFLINE_3, new Object[]{userId, playerId, brokenTypeCode});
        });
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#bindPlayerSession()
    */
    public boolean bindPlayerSession(CallPoint remote, String userId, long playerId, ClientSessionRef session){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_BINDPLAYERSESSION_0, new Object[]{userId, playerId, session});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#enterMap()
    */
    public void enterMap(CallPoint remote, long playerId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERSERVICE_ENTERMAP_1, new Object[]{playerId});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#getOnlineCount()
    */
    public int getOnlineCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_GETONLINECOUNT_2, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#onPlayerOffline()
    */
    public void onPlayerOffline(CallPoint remote, String userId, long playerId, int brokenTypeCode){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERSERVICE_ONPLAYEROFFLINE_3, new Object[]{userId, playerId, brokenTypeCode});
    }


}
