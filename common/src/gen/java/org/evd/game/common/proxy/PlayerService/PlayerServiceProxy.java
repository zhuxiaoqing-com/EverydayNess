package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.common.proto.RoleData;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.call.CallPoint;

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
        public final static int ENUM_PLAYERSERVICE_ENTERMAP_0 = 0;
        public final static int ENUM_PLAYERSERVICE_GETONLINECOUNT_1 = 1;
        public final static int ENUM_PLAYERSERVICE_LOGINPLAYER_2 = 2;
        public final static int ENUM_PLAYERSERVICE_ONPLAYEROFFLINE_3 = 3;
        public final static int ENUM_PLAYERSERVICE_ONLINEPLAYER_4 = 4;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendEnterMap(CallPoint remote, long playerId){
        return RpcResult.run(() -> inst().enterMap(remote, playerId));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Integer> callGetOnlineCount(CallPoint remote){
        return RpcResult.call(() -> inst().getOnlineCount(remote));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<ActorAddress> callLoginPlayer(CallPoint remote, String userId, RoleData role, ClientSessionRef session){
        return RpcResult.call(() -> inst().loginPlayer(remote, userId, role, session));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendOnPlayerOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId, int brokenTypeCode){
        return RpcResult.run(() -> inst().onPlayerOffline(remote, userId, playerId, gate, gateSessionId, brokenTypeCode));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendOnlinePlayer(CallPoint remote, String userId, long playerId, ClientSessionRef session, ActorAddress gateActorAddress){
        return RpcResult.run(() -> inst().onlinePlayer(remote, userId, playerId, session, gateActorAddress));
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#enterMap()
    */
    public void enterMap(CallPoint remote, long playerId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERSERVICE_ENTERMAP_0, new Object[]{playerId});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#getOnlineCount()
    */
    public int getOnlineCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_GETONLINECOUNT_1, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#loginPlayer()
    */
    public ActorAddress loginPlayer(CallPoint remote, String userId, RoleData role, ClientSessionRef session){
        Service service = Service.getCurrent();
        return (ActorAddress)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_LOGINPLAYER_2, new Object[]{userId, role, session});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#onPlayerOffline()
    */
    public void onPlayerOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId, int brokenTypeCode){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERSERVICE_ONPLAYEROFFLINE_3, new Object[]{userId, playerId, gate, gateSessionId, brokenTypeCode});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#onlinePlayer()
    */
    public void onlinePlayer(CallPoint remote, String userId, long playerId, ClientSessionRef session, ActorAddress gateActorAddress){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERSERVICE_ONLINEPLAYER_4, new Object[]{userId, playerId, session, gateActorAddress});
    }


}
