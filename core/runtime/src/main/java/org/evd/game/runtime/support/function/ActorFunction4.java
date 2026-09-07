package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorFunction4<T1, T2, T3> {
    void apply(ActorId actorId, T1 t1, T2 t2, T3 t3) throws InterruptedException;
}
