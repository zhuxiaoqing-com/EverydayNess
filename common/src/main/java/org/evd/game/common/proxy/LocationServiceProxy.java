package org.evd.game.common.proxy;

import org.evd.game.common.location.LocationRpcEnum;
import org.evd.game.runtime.DistributeConfig;
import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/**
 * 手写 LocationService 代理，供各业务服务按 actorId 查询 location。
 */
public class LocationServiceProxy extends RPCProxyBase {

    private LocationServiceProxy(CallPoint callPoint) {
        this.remote = callPoint;
    }

    public static LocationServiceProxy inst(CallPoint callPoint) {
        return new LocationServiceProxy(callPoint);
    }

    public static LocationServiceProxy inst() {
        CallPoint callPoint = DistributeConfig.getNodeByServiceClass("org.evd.game.LocationService.LocationService", 0L);
        if (callPoint == null) {
            throw new IllegalStateException("找不到 LocationService 服务路由: org.evd.game.LocationService.LocationService");
        }
        return new LocationServiceProxy(callPoint);
    }

    public void bindActor(long actorId, CallPoint callPoint) {
        Service service = Service.getCurrent();
        service.call(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_BINDACTOR_LONG_ORG_EVD_GAME_RUNTIME_CALL_CALLPOINT,
                new Object[]{actorId, callPoint});
    }

    public void unbindActor(long actorId, CallPoint callPoint) {
        Service service = Service.getCurrent();
        service.call(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_UNBINDACTOR_LONG_ORG_EVD_GAME_RUNTIME_CALL_CALLPOINT,
                new Object[]{actorId, callPoint});
    }

    public CallPoint getActor(long actorId) {
        Service service = Service.getCurrent();
        return (CallPoint) service.callWait(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_ORG_EVD_GAME_RUNTIME_CALL_CALLPOINT_GETACTOR_LONG,
                new Object[]{actorId});
    }
}
