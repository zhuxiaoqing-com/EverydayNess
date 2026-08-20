package org.evd.game.common.proxy.LobbyService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.LobbyService.LobbyRoleSnapshot;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.LobbyService.LobbyUserAccessResult;

/**
* 根据LobbyServiceService生成的代理类
*/
public final class LobbyServiceProxy {

    private static final LobbyServiceProxy INSTANCE = new LobbyServiceProxy();

    private LobbyServiceProxy() {
    }

    public static LobbyServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_LOBBYSERVICE_GETROLE_0 = 0;
        public final static int ENUM_LOBBYSERVICE_PLAYERONLINE_1 = 1;
        public final static int ENUM_LOBBYSERVICE_VALIDATEORCREATEUSER_2 = 2;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<LobbyRoleSnapshot> callGetRole(CallPoint remote, String userId){
        return RpcResult.call(() -> inst().getRole(remote, userId));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendPlayerOnline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId){
        return RpcResult.run(() -> inst().playerOnline(remote, userId, playerId, gate, gateSessionId));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<LobbyUserAccessResult> callValidateOrCreateUser(CallPoint remote, String userId){
        return RpcResult.call(() -> inst().validateOrCreateUser(remote, userId));
    }



    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyService#getRole()
    */
    public LobbyRoleSnapshot getRole(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        return (LobbyRoleSnapshot)service.callWait(remote, EnumCall.ENUM_LOBBYSERVICE_GETROLE_0, new Object[]{userId});
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyService#playerOnline()
    */
    public void playerOnline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOBBYSERVICE_PLAYERONLINE_1, new Object[]{userId, playerId, gate, gateSessionId});
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyService#validateOrCreateUser()
    */
    public LobbyUserAccessResult validateOrCreateUser(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        return (LobbyUserAccessResult)service.callWait(remote, EnumCall.ENUM_LOBBYSERVICE_VALIDATEORCREATEUSER_2, new Object[]{userId});
    }


}
