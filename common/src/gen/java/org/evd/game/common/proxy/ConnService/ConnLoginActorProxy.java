package org.evd.game.common.proxy.ConnService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/**
* 根据ConnLoginActorService生成的代理类
*/
public final class ConnLoginActorProxy {

    private static final ConnLoginActorProxy INSTANCE = new ConnLoginActorProxy();

    private ConnLoginActorProxy() {
    }

    public static ConnLoginActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNLOGINACTOR_BINDPLAYER_10 = 10;
        public final static int ENUM_CONNLOGINACTOR_REGISTERLOGIN_11 = 11;
        public final static int ENUM_CONNLOGINACTOR_REJECTPENDINGLOGIN_12 = 12;
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
    * 对应源方法: org.evd.game.ConnService.login.ConnLoginActor#bindPlayer()
    */
    public ActorAddress bindPlayer(CallPoint remote, long sessionId, long playerId, ActorAddress playerActorAddress){
        Service service = Service.getCurrent();
        return (ActorAddress)service.callWait(remote, EnumCall.ENUM_CONNLOGINACTOR_BINDPLAYER_10, new Object[]{sessionId, playerId, playerActorAddress});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.login.ConnLoginActor#registerLogin()
    */
    public boolean registerLogin(CallPoint remote, long sessionId, String userId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNLOGINACTOR_REGISTERLOGIN_11, new Object[]{sessionId, userId});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.login.ConnLoginActor#rejectPendingLogin()
    */
    public boolean rejectPendingLogin(CallPoint remote, long sessionId, String userId, String token, ClientFrameChunk packet, int brokenTypeCode, String reason){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNLOGINACTOR_REJECTPENDINGLOGIN_12, new Object[]{sessionId, userId, token, packet, brokenTypeCode, reason});
    }


}
