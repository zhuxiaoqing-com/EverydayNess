package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorReturnFunction1<R> {
    R apply(ActorId actorId);
}
