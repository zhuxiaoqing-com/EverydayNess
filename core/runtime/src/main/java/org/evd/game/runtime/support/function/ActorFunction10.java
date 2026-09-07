package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorFunction10<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
    void apply(ActorId actorId, T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8, T9 t9) throws InterruptedException;
}
