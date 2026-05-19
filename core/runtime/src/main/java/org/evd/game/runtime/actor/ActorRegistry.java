package org.evd.game.runtime.actor;

import org.evd.game.runtime.support.RpcCallException;

import java.util.HashMap;
import java.util.Map;

public class ActorRegistry {
    public static final class Registration {
        private final Object actor;
        private final ActorExecutionMode executionMode;

        private Registration(Object actor, ActorExecutionMode executionMode) {
            this.actor = actor;
            this.executionMode = executionMode;
        }

        public Object getActor() {
            return actor;
        }

        public ActorExecutionMode getExecutionMode() {
            return executionMode;
        }
    }

    private final Map<ActorId, Registration> actors = new HashMap<>();

    public void register(ActorId actorId, Object actor, ActorExecutionMode executionMode) {
        actors.put(new ActorId(actorId), new Registration(actor, executionMode));
    }

    public void unregister(ActorId actorId) {
        actors.remove(actorId);
    }

    public boolean contains(ActorId actorId) {
        return actors.containsKey(actorId);
    }

    public Object get(ActorId actorId) {
        Registration registration = actors.get(actorId);
        return registration == null ? null : registration.getActor();
    }

    public Registration requireRegistration(ActorId actorId) {
        Registration registration = actors.get(actorId);
        if (registration == null) {
            throw RpcCallException.actorNotFound(actorId);
        }
        return registration;
    }

    public <T> T require(ActorId actorId, Class<T> type) {
        Object actor = requireRegistration(actorId).getActor();
        if (!type.isInstance(actor)) {
            throw RpcCallException.actorTypeMismatch(actorId, type, actor.getClass());
        }
        return type.cast(actor);
    }
}
