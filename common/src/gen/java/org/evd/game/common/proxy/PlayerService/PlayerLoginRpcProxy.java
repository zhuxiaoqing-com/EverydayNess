package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.common.proto.RoleData;
import org.evd.game.runtime.client.ClientSessionRef;

/**
* 根据PlayerLoginRpcService生成的代理类
*/
public final class PlayerLoginRpcProxy {

    private static final PlayerLoginRpcProxy INSTANCE = new PlayerLoginRpcProxy();

    private PlayerLoginRpcProxy() {
    }

    public static PlayerLoginRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_PLAYERLOGINRPC_BINDGATEACTORADDRESS_1 = 1;
        public final static int ENUM_PLAYERLOGINRPC_LOGINPLAYER_2 = 2;
        public final static int ENUM_PLAYERLOGINRPC_ONLINEPLAYER_3 = 3;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendBindGateActorAddress(CallPoint remote, long playerId, ActorAddress gateActorAddress){
        return RpcResult.run(() -> inst().bindGateActorAddress(remote, playerId, gateActorAddress));
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
    public static RpcResult<Void> sendOnlinePlayer(CallPoint remote, String userId, long playerId, RoleData role, ClientSessionRef session){
        return RpcResult.run(() -> inst().onlinePlayer(remote, userId, playerId, role, session));
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.login.PlayerLoginRpc#bindGateActorAddress()
    */
    public void bindGateActorAddress(CallPoint remote, long playerId, ActorAddress gateActorAddress){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERLOGINRPC_BINDGATEACTORADDRESS_1, new Object[]{playerId, gateActorAddress});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.login.PlayerLoginRpc#loginPlayer()
    */
    public ActorAddress loginPlayer(CallPoint remote, String userId, RoleData role, ClientSessionRef session){
        Service service = Service.getCurrent();
        return (ActorAddress)service.callWait(remote, EnumCall.ENUM_PLAYERLOGINRPC_LOGINPLAYER_2, new Object[]{userId, role, session});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.login.PlayerLoginRpc#onlinePlayer()
    */
    public void onlinePlayer(CallPoint remote, String userId, long playerId, RoleData role, ClientSessionRef session){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERLOGINRPC_ONLINEPLAYER_3, new Object[]{userId, playerId, role, session});
    }


}
