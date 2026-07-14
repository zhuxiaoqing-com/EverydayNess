package org.evd.game.common.proxy.LobbyService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据LobbyOfflineActorService生成的代理类
*/
public final class LobbyOfflineActorProxy {

    private static final LobbyOfflineActorProxy INSTANCE = new LobbyOfflineActorProxy();

    private LobbyOfflineActorProxy() {
    }

    public static LobbyOfflineActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_LOBBYOFFLINEACTOR_ONSESSIONOFFLINE_0 = 0;
    }

    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callOnSessionOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long sessionId, int brokenTypeCode){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_LOBBYOFFLINEACTOR_ONSESSIONOFFLINE_0, new Object[]{userId, playerId, gate, sessionId, brokenTypeCode});
        });
    }


    /**
    * 对应源方法: org.evd.game.LobbyService.LobbyOfflineActor#onSessionOffline()
    */
    public void onSessionOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long sessionId, int brokenTypeCode){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOBBYOFFLINEACTOR_ONSESSIONOFFLINE_0, new Object[]{userId, playerId, gate, sessionId, brokenTypeCode});
    }


}
