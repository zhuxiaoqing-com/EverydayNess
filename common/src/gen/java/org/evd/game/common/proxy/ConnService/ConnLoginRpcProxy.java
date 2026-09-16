package org.evd.game.common.proxy.ConnService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/**
* 根据ConnLoginRpcService生成的代理类
*/
public final class ConnLoginRpcProxy {

    private static final ConnLoginRpcProxy INSTANCE = new ConnLoginRpcProxy();

    private ConnLoginRpcProxy() {
    }

    public static ConnLoginRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNLOGINRPC_BINDPLAYER_8 = 8;
        public final static int ENUM_CONNLOGINRPC_REGISTERLOGIN_9 = 9;
        public final static int ENUM_CONNLOGINRPC_REJECTPENDINGLOGIN_10 = 10;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<ActorAddress> callBindPlayer(CallPoint remote, long sessionId, long playerId, ActorAddress playerActorAddress){
        return RpcResult.call(() -> inst().bindPlayer(remote, sessionId, playerId, playerActorAddress));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callRegisterLogin(CallPoint remote, long sessionId, String userId){
        return RpcResult.call(() -> inst().registerLogin(remote, sessionId, userId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callRejectPendingLogin(CallPoint remote, long sessionId, String userId, String token, ClientFrameChunk packet, int brokenTypeCode, String reason){
        return RpcResult.call(() -> inst().rejectPendingLogin(remote, sessionId, userId, token, packet, brokenTypeCode, reason));
    }



    /**
    * 对应源方法: org.evd.game.ConnService.login.ConnLoginRpc#bindPlayer()
    */
    public ActorAddress bindPlayer(CallPoint remote, long sessionId, long playerId, ActorAddress playerActorAddress){
        Service service = Service.getCurrent();
        return (ActorAddress)service.callWait(remote, EnumCall.ENUM_CONNLOGINRPC_BINDPLAYER_8, new Object[]{sessionId, playerId, playerActorAddress});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.login.ConnLoginRpc#registerLogin()
    */
    public boolean registerLogin(CallPoint remote, long sessionId, String userId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNLOGINRPC_REGISTERLOGIN_9, new Object[]{sessionId, userId});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.login.ConnLoginRpc#rejectPendingLogin()
    */
    public boolean rejectPendingLogin(CallPoint remote, long sessionId, String userId, String token, ClientFrameChunk packet, int brokenTypeCode, String reason){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNLOGINRPC_REJECTPENDINGLOGIN_10, new Object[]{sessionId, userId, token, packet, brokenTypeCode, reason});
    }


}
