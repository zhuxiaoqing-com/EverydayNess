package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;

/**
 * @author zhuxiaoqing
 * @Description: LocationInterface
 * @Date 2026/6/18 20:02
 **/
public interface LocationInterface {

    public void add(CallPoint remote, ActorId actorId, ActorAddress actorAddress);

    public ActorAddress get(CallPoint remote, ActorId actorId);

    public ActorAddress get(CallPoint remote, ActorId actorId, long timeoutMillis);

    public void remove(CallPoint remote, ActorId actorId);

    public void lock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, int timeMillis);

    public void unlock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress);
}
