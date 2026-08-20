package org.evd.game.common.proxy.LobbyService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.common.proto.C2S_CreateRole;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据LobbyRoleActorService生成的代理类
*/
public final class LobbyRoleActorProxy {

    private static final LobbyRoleActorProxy INSTANCE = new LobbyRoleActorProxy();

    private LobbyRoleActorProxy() {
    }

    public static LobbyRoleActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_LOBBYROLEACTOR_CREATEROLE_3 = 3;
        public final static int ENUM_LOBBYROLEACTOR_ROLELIST_4 = 4;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendCreateRole(CallPoint remote, ClientSessionRef session, C2S_CreateRole req){
        return RpcResult.run(() -> inst().createRole(remote, session, req));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRoleList(CallPoint remote, CallPoint gate, long gateSessionId, String userId){
        return RpcResult.run(() -> inst().roleList(remote, gate, gateSessionId, userId));
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyRoleActor#createRole()
    */
    public void createRole(CallPoint remote, ClientSessionRef session, C2S_CreateRole req){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOBBYROLEACTOR_CREATEROLE_3, new Object[]{session, req});
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyRoleActor#roleList()
    */
    public void roleList(CallPoint remote, CallPoint gate, long gateSessionId, String userId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOBBYROLEACTOR_ROLELIST_4, new Object[]{gate, gateSessionId, userId});
    }


}
