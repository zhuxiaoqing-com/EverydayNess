package org.evd.game.common.proxy.LobbyService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.serializeBean.LobbyService.role.LobbyRoleSnapshot;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.LobbyService.login.LobbyUserAccessResult;

/**
* 根据LobbyServiceRpcService生成的代理类
*/
public final class LobbyServiceRpcProxy {

    private static final LobbyServiceRpcProxy INSTANCE = new LobbyServiceRpcProxy();

    private LobbyServiceRpcProxy() {
    }

    public static LobbyServiceRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_LOBBYSERVICERPC_GETROLE_2 = 2;
        public final static int ENUM_LOBBYSERVICERPC_PLAYERONLINE_3 = 3;
        public final static int ENUM_LOBBYSERVICERPC_VALIDATEORCREATEUSER_4 = 4;
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
    * 对应源方法: org.evd.game.LobbyService.LobbyServiceRpc#getRole()
    */
    public LobbyRoleSnapshot getRole(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        }
        return (LobbyRoleSnapshot)service.callWait(remote, EnumCall.ENUM_LOBBYSERVICERPC_GETROLE_2, new Object[]{userId});
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyServiceRpc#playerOnline()
    */
    public void playerOnline(CallPoint remote, String userId, long playerId, CallPoint gate, long gateSessionId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        }
        service.call(remote, EnumCall.ENUM_LOBBYSERVICERPC_PLAYERONLINE_3, new Object[]{userId, playerId, gate, gateSessionId});
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyServiceRpc#validateOrCreateUser()
    */
    public LobbyUserAccessResult validateOrCreateUser(CallPoint remote, String userId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        }
        return (LobbyUserAccessResult)service.callWait(remote, EnumCall.ENUM_LOBBYSERVICERPC_VALIDATEORCREATEUSER_4, new Object[]{userId});
    }


}
