package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorReturnFunction2<R, T1> {
    R apply(ActorId actorId, T1 t1);
}
