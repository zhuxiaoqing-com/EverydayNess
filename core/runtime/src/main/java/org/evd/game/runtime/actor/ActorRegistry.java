package org.evd.game.runtime.actor;

import org.evd.game.runtime.support.RpcCallException;

import java.util.HashMap;
import java.util.Map;

public class ActorRegistry {
    public static final class Registration {
        private final Object actor;
        private final ActorExecutionMode executionMode;
        private final long registrationId;

        private Registration(Object actor, ActorExecutionMode executionMode, long registrationId) {
            this.actor = actor;
            this.executionMode = executionMode;
            this.registrationId = registrationId;
        }

        public Object getActor() {
            return actor;
        }

        public ActorExecutionMode getExecutionMode() {
            return executionMode;
        }

        public long getRegistrationId() {
            return registrationId;
        }
    }

    private final Map<ActorId, Registration> actors = new HashMap<>();
    private long nextRegistrationId = 1L;

    public void register(ActorId actorId, Object actor, ActorExecutionMode executionMode) {
        actors.put(new ActorId(actorId), new Registration(actor, executionMode, nextRegistrationId++));
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

    public Registration requireSameRegistration(ActorId actorId, long registrationId) {
        Registration registration = requireRegistration(actorId);
        if (registration.getRegistrationId() != registrationId) {
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
