package org.evd.game.common.proxy;

import org.evd.game.common.location.LocationRpcEnum;
import org.evd.game.runtime.DistributeConfig;
import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;

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

    public void add(ActorId actorId, ActorAddress actorAddress) {
        Service service = Service.getCurrent();
        service.call(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_ADD_ACTORID_ACTORADDRESS,
                new Object[]{actorId, actorAddress});
    }

    public void remove(ActorId actorId) {
        Service service = Service.getCurrent();
        service.call(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_REMOVE_ACTORID,
                new Object[]{actorId});
    }

    public ActorAddress get(ActorId actorId) {
        Service service = Service.getCurrent();
        return (ActorAddress) service.callWait(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_ACTORADDRESS_GET_ACTORID,
                new Object[]{actorId});
    }

    public void lock(ActorId actorId, ActorAddress oldActorAddress) {
        lock(actorId, oldActorAddress, 60000);
    }

    public void lock(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        Service service = Service.getCurrent();
        service.call(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_LOCK_ACTORID_ACTORADDRESS_INT,
                new Object[]{actorId, oldActorAddress, timeMillis});
    }

    public void unlock(ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress) {
        Service service = Service.getCurrent();
        service.call(remote,
                LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_UNLOCK_ACTORID_ACTORADDRESS_ACTORADDRESS,
                new Object[]{actorId, oldActorAddress, newActorAddress});
    }
}
