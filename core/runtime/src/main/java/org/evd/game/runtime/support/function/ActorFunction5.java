package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorFunction5<T1, T2, T3, T4> {
    void apply(ActorId actorId, T1 t1, T2 t2, T3 t3, T4 t4) throws InterruptedException;
}
