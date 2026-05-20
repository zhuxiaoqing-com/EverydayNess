package org.evd.game.LocationService;

import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.function.*;
        import org.evd.game.runtime.actor.ActorId;
        import org.evd.game.runtime.actor.ActorAddress;

/**
* 根据LocationServiceService生成的rpc分发类
*/
public class LocationServiceImpl extends RPCImplBase {
    public final static class EnumCall{
        public final static int ENUM_LOCATIONSERVICE_VOID_ADD_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS = 0;
        public final static int ENUM_LOCATIONSERVICE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_GET_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID = 1;
        public final static int ENUM_LOCATIONSERVICE_VOID_LOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_INT = 2;
        public final static int ENUM_LOCATIONSERVICE_VOID_REMOVE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID = 3;
        public final static int ENUM_LOCATIONSERVICE_VOID_UNLOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS = 4;
    }

    @Override
    public Object getMethodFunction(Service serv, int methodKey) {
        LocationService service = (LocationService) serv;
        switch (methodKey){
            case EnumCall.ENUM_LOCATIONSERVICE_VOID_ADD_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS:
                return (Function2<org.evd.game.runtime.actor.ActorId, org.evd.game.runtime.actor.ActorAddress>)service::add;
            case EnumCall.ENUM_LOCATIONSERVICE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_GET_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID:
                return (ReturnFunction1<org.evd.game.runtime.actor.ActorAddress, org.evd.game.runtime.actor.ActorId>)service::get;
            case EnumCall.ENUM_LOCATIONSERVICE_VOID_LOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_INT:
                return (Function3<org.evd.game.runtime.actor.ActorId, org.evd.game.runtime.actor.ActorAddress, Integer>)service::lock;
            case EnumCall.ENUM_LOCATIONSERVICE_VOID_REMOVE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID:
                return (Function1<org.evd.game.runtime.actor.ActorId>)service::remove;
            case EnumCall.ENUM_LOCATIONSERVICE_VOID_UNLOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS:
                return (Function3<org.evd.game.runtime.actor.ActorId, org.evd.game.runtime.actor.ActorAddress, org.evd.game.runtime.actor.ActorAddress>)service::unlock;
            default:
                return null;
        }
    }
}
