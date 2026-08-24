package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlinePlayerCandidate;

/**
* 根据OnlineRoutingActorService生成的代理类
*/
public final class OnlineRoutingActorProxy {

    private static final OnlineRoutingActorProxy INSTANCE = new OnlineRoutingActorProxy();

    private OnlineRoutingActorProxy() {
    }

    public static OnlineRoutingActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINEROUTINGACTOR_SELECTLEASTLOADEDCONN_7 = 7;
        public final static int ENUM_ONLINEROUTINGACTOR_SELECTLEASTLOADEDPLAYER_8 = 8;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<OnlineConnCandidate> callSelectLeastLoadedConn(CallPoint remote){
        return RpcResult.call(() -> inst().selectLeastLoadedConn(remote));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<OnlinePlayerCandidate> callSelectLeastLoadedPlayer(CallPoint remote){
        return RpcResult.call(() -> inst().selectLeastLoadedPlayer(remote));
    }



    /**
    * 对应源方法: org.evd.game.OnlineService.routing.OnlineRoutingActor#selectLeastLoadedConn()
    */
    public OnlineConnCandidate selectLeastLoadedConn(CallPoint remote){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (OnlineConnCandidate)service.callWait(remote, EnumCall.ENUM_ONLINEROUTINGACTOR_SELECTLEASTLOADEDCONN_7, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.routing.OnlineRoutingActor#selectLeastLoadedPlayer()
    */
    public OnlinePlayerCandidate selectLeastLoadedPlayer(CallPoint remote){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (OnlinePlayerCandidate)service.callWait(remote, EnumCall.ENUM_ONLINEROUTINGACTOR_SELECTLEASTLOADEDPLAYER_8, new Object[]{});
    }


}
