package org.evd.game.runtime;

public interface ActorManager {
    <T> T getActor(Class<T> actorType);
}
