package org.evd.game.runtime.rpcProxyInterface;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;

/**
 * @author zhuxiaoqing
 **/
public interface LocationInterface {

    void add(CallPoint remote, ActorId actorId, ActorAddress actorAddress);

    ActorAddress get(CallPoint remote, ActorId actorId);

//    public ActorAddress get(CallPoint remote, ActorId actorId, long timeoutMillis);

    void remove(CallPoint remote, ActorId actorId, ActorAddress expectedActorAddress);

    void lock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, int timeMillis);

    void unlock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress);
}
