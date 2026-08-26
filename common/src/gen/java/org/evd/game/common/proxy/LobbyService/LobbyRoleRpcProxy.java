package org.evd.game.common.proxy.LobbyService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.common.proto.C2S_CreateRole;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据LobbyRoleRpcService生成的代理类
*/
public final class LobbyRoleRpcProxy {

    private static final LobbyRoleRpcProxy INSTANCE = new LobbyRoleRpcProxy();

    private LobbyRoleRpcProxy() {
    }

    public static LobbyRoleRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_LOBBYROLERPC_CREATEROLE_0 = 0;
        public final static int ENUM_LOBBYROLERPC_ROLELIST_1 = 1;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendCreateRole(CallPoint remote, ClientSessionRef session, C2S_CreateRole request){
        return RpcResult.run(() -> inst().createRole(remote, session, request));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRoleList(CallPoint remote, CallPoint gate, long gateSessionId, String userId){
        return RpcResult.run(() -> inst().roleList(remote, gate, gateSessionId, userId));
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyRoleRpc#createRole()
    */
    public void createRole(CallPoint remote, ClientSessionRef session, C2S_CreateRole request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        }
        service.call(remote, EnumCall.ENUM_LOBBYROLERPC_CREATEROLE_0, new Object[]{session, request});
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyRoleRpc#roleList()
    */
    public void roleList(CallPoint remote, CallPoint gate, long gateSessionId, String userId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        }
        service.call(remote, EnumCall.ENUM_LOBBYROLERPC_ROLELIST_1, new Object[]{gate, gateSessionId, userId});
    }


}
