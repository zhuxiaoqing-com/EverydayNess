package org.evd.game.common.proxy.PlayerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;

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
        public final static int ENUM_PLAYERSERVICE_BINDPLAYERSESSION_0 = 0;
        public final static int ENUM_PLAYERSERVICE_ENTERMAP_1 = 1;
        public final static int ENUM_PLAYERSERVICE_GETONLINECOUNT_2 = 2;
        public final static int ENUM_PLAYERSERVICE_ONPLAYEROFFLINE_3 = 3;
    }

    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#bindPlayerSession()
    */
    public boolean bindPlayerSession(CallPoint remote, String userId, long playerId, ClientSessionRef session){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_BINDPLAYERSESSION_0, new Object[]{userId, playerId, session});
    }

    public boolean bindPlayerSession(CallPoint remote, String userId, long playerId, ClientSessionRef session, long timeoutMillis){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_BINDPLAYERSESSION_0, new Object[]{userId, playerId, session}, timeoutMillis);
    }

    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#enterMap()
    */
    public void enterMap(CallPoint remote, long playerId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERSERVICE_ENTERMAP_1, new Object[]{playerId});
    }


    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#getOnlineCount()
    */
    public int getOnlineCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_GETONLINECOUNT_2, new Object[]{});
    }

    public int getOnlineCount(CallPoint remote, long timeoutMillis){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_PLAYERSERVICE_GETONLINECOUNT_2, new Object[]{}, timeoutMillis);
    }

    /**
    * 对应源方法: org.evd.game.PlayerService.PlayerService#onPlayerOffline()
    */
    public void onPlayerOffline(CallPoint remote, String userId, long playerId, int brokenTypeCode){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_PLAYERSERVICE_ONPLAYEROFFLINE_3, new Object[]{userId, playerId, brokenTypeCode});
    }


}
