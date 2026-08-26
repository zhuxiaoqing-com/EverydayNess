package org.evd.game.LocationService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;

/** LocationService 地址和锁 RPC 入口。 */
@Actor
@RpcHandler
public final class LocationServiceRpc {
    @Rpc
    public void add(ActorId actorId, ActorAddress actorAddress) {
        owner().add(actorId, actorAddress);
    }

    @Rpc
    public void remove(ActorId actorId, ActorAddress expectedActorAddress) {
        owner().remove(actorId, expectedActorAddress);
    }

    @Rpc
    public void lock(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        owner().lock(actorId, oldActorAddress, timeMillis);
    }

    @Rpc
    public void unlock(ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress) {
        owner().unlock(actorId, oldActorAddress, newActorAddress);
    }

    @Rpc
    public ActorAddress get(ActorId actorId) {
        return owner().get(actorId);
    }

    private LocationService owner() {
        return Service.getCurrent(LocationService.class);
    }
}
