package org.evd.game.common.proxy.LobbyService;

import org.evd.game.runtime.Service;
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
    * 对应源方法: org.evd.game.LobbyService.LobbyOfflineActor#onSessionOffline()
    */
    public void onSessionOffline(CallPoint remote, String userId, long playerId, CallPoint gate, long sessionId, int brokenTypeCode){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOBBYOFFLINEACTOR_ONSESSIONOFFLINE_0, new Object[]{userId, playerId, gate, sessionId, brokenTypeCode});
    }


}
