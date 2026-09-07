package org.evd.game.runtime.support.function;

import org.evd.game.runtime.actor.ActorId;

@FunctionalInterface
public interface ActorFunction1 {
    void apply(ActorId actorId) throws InterruptedException;
}
