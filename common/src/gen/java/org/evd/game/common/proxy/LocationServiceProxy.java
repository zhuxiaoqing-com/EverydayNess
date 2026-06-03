package org.evd.game.common.proxy;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
        import org.evd.game.runtime.actor.ActorId;
        import org.evd.game.runtime.actor.ActorAddress;

/**
* 根据LocationServiceService生成的代理类
*/
public final class LocationServiceProxy {

    private LocationServiceProxy() {
    }


    public final static class EnumCall{
        public final static int ENUM_LOCATIONSERVICE_VOID_ADD_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS = 0;
        public final static int ENUM_LOCATIONSERVICE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_GET_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID = 0;
        public final static int ENUM_LOCATIONSERVICE_VOID_LOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_INT = 0;
        public final static int ENUM_LOCATIONSERVICE_VOID_REMOVE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID = 0;
        public final static int ENUM_LOCATIONSERVICE_VOID_UNLOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS = 0;
    }

    /**
    * @see org.evd.game.LocationService.LocationService#add()
    */
    public static void add(CallPoint remote, org.evd.game.runtime.actor.ActorId actorId, org.evd.game.runtime.actor.ActorAddress actorAddress){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_VOID_ADD_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS, new Object[]{actorId, actorAddress});
    }
    /**
    * @see org.evd.game.LocationService.LocationService#get()
    */
    public static org.evd.game.runtime.actor.ActorAddress get(CallPoint remote, org.evd.game.runtime.actor.ActorId actorId){
        Service service = Service.getCurrent();
        return (org.evd.game.runtime.actor.ActorAddress)service.callWait(remote, EnumCall.ENUM_LOCATIONSERVICE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_GET_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID, new Object[]{actorId});
    }
    public static org.evd.game.runtime.actor.ActorAddress get(CallPoint remote, org.evd.game.runtime.actor.ActorId actorId, long timeoutMillis){
        Service service = Service.getCurrent();
        return (org.evd.game.runtime.actor.ActorAddress)service.callWait(remote, EnumCall.ENUM_LOCATIONSERVICE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_GET_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID, new Object[]{actorId}, timeoutMillis);
    }
    /**
    * @see org.evd.game.LocationService.LocationService#lock()
    */
    public static void lock(CallPoint remote, org.evd.game.runtime.actor.ActorId actorId, org.evd.game.runtime.actor.ActorAddress oldActorAddress, int timeMillis){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_VOID_LOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_INT, new Object[]{actorId, oldActorAddress, timeMillis});
    }
    /**
    * @see org.evd.game.LocationService.LocationService#remove()
    */
    public static void remove(CallPoint remote, org.evd.game.runtime.actor.ActorId actorId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_VOID_REMOVE_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID, new Object[]{actorId});
    }
    /**
    * @see org.evd.game.LocationService.LocationService#unlock()
    */
    public static void unlock(CallPoint remote, org.evd.game.runtime.actor.ActorId actorId, org.evd.game.runtime.actor.ActorAddress oldActorAddress, org.evd.game.runtime.actor.ActorAddress newActorAddress){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_VOID_UNLOCK_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORID_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS_ORG_EVD_GAME_RUNTIME_ACTOR_ACTORADDRESS, new Object[]{actorId, oldActorAddress, newActorAddress});
    }
}
