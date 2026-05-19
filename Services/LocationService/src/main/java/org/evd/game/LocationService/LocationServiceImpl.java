package org.evd.game.LocationService;

import org.evd.game.common.location.LocationRpcEnum;
import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
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
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_BINDACTOR_LONG_ORG_EVD_GAME_RUNTIME_CALL_CALLPOINT ->
                (Function2<ActorId, CallPoint>) service::bindActor;
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_VOID_UNBINDACTOR_LONG_ORG_EVD_GAME_RUNTIME_CALL_CALLPOINT ->
                (Function2<ActorId, CallPoint>) service::unbindActor;
            case LocationRpcEnum.ENUM_LOCATIONSERVICE_ORG_EVD_GAME_RUNTIME_CALL_CALLPOINT_GETACTOR_LONG ->
                (ReturnFunction1<CallPoint, ActorId>) service::getActor;
            default -> throw new IllegalArgumentException("未知的LocationService methodKey: " + methodKey);
        };
    }
}
