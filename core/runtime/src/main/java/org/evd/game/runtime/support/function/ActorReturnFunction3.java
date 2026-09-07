package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorReturnFunction3<R, T1, T2> {
    R apply(ActorId actorId, T1 t1, T2 t2);
}
