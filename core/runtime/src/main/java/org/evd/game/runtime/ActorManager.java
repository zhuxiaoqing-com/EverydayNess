package org.evd.game.runtime;

import java.util.Map;

public interface ActorManager {
    <T> T getActor(Class<T> actorType);

    Map<Class<?>, Object> getActors();
}
