package org.evd.game.LocationService;

import org.evd.game.common.location.LocationRpcEnum;
import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.function.Function2;
import org.evd.game.runtime.support.function.ReturnFunction1;

/**
 * 手写 LocationService 的 RPC 分发表，避免把现有 APT 生成链一起改动。
 */
public class LocationServiceImpl extends RPCImplBase {
    @Override
    public Object getMethodFunction(Service serv, int methodKey) {
        LocationService service = (LocationService) serv;
        return switch (methodKey) {
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_ADD_ACTORID_ACTORADDRESS ->
                (Function2<ActorId, ActorAddress>) service::add;
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_REMOVE_ACTORID ->
                (org.evd.game.runtime.support.function.Function1<ActorId>) service::remove;
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_ACTORADDRESS_GET_ACTORID ->
                (ReturnFunction1<ActorAddress, ActorId>) service::get;
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_LOCK_ACTORID_ACTORADDRESS_INT ->
                (org.evd.game.runtime.support.function.Function3<ActorId, ActorAddress, Integer>) service::lock;
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_UNLOCK_ACTORID_ACTORADDRESS_ACTORADDRESS ->
                (org.evd.game.runtime.support.function.Function3<ActorId, ActorAddress, ActorAddress>) service::unlock;
            default -> throw new IllegalArgumentException("未知的LocationService methodKey: " + methodKey);
        };
    }
}
