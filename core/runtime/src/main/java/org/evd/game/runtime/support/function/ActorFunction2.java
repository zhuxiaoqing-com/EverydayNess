package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorFunction2<T1> {
    void apply(ActorId actorId, T1 t1) throws InterruptedException;
}
