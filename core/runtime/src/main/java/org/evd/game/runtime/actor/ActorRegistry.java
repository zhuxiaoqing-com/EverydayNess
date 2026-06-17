package org.evd.game.runtime.actor;

import org.evd.game.runtime.mailbox.MailBoxComponent;
import org.evd.game.runtime.mailbox.MailBoxType;
import org.evd.game.runtime.support.RpcCallException;

import java.util.HashMap;
import java.util.Map;

public class ActorRegistry {
    public static final class Registration {
        private final Object actor;
        private final ActorExecutionMode executionMode;
        private final MailBoxComponent mailBoxComponent;

        private Registration(
                Object actor,
                ActorExecutionMode executionMode,
                MailBoxComponent mailBoxComponent
        ) {
            this.actor = actor;
            this.executionMode = executionMode;
            this.mailBoxComponent = mailBoxComponent;
        }

        public Object getActor() {
            return actor;
        }

        public ActorExecutionMode getExecutionMode() {
            return executionMode;
        }

        public MailBoxComponent getMailBoxComponent() {
            return mailBoxComponent;
        }
    }

    private final Map<ActorId, Registration> actors = new HashMap<>();
    private long nextMailBoxEpoch = 1L;

    public void register(ActorId actorId, Object actor, ActorExecutionMode executionMode) {
        ActorId key = new ActorId(actorId);
        MailBoxComponent mailBoxComponent = new MailBoxComponent(
                key,
                nextMailBoxEpoch++,
                executionMode == ActorExecutionMode.ORDERED ? MailBoxType.ORDERED : MailBoxType.UNORDERED);
        actors.put(key, new Registration(actor, executionMode, mailBoxComponent));
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

    public MailBoxComponent getMailBox(ActorId actorId) {
        Registration registration = actors.get(actorId);
        return registration == null ? null : registration.getMailBoxComponent();
    }

    public boolean hasSameMailBoxEpoch(ActorId actorId, long mailBoxEpoch) {
        MailBoxComponent mailBoxComponent = getMailBox(actorId);
        return mailBoxComponent != null && mailBoxComponent.getEpoch() == mailBoxEpoch;
    }

    public <T> T require(ActorId actorId, Class<T> type) {
        Object actor = requireRegistration(actorId).getActor();
        if (!type.isInstance(actor)) {
            throw RpcCallException.actorTypeMismatch(actorId, type, actor.getClass());
        }
        return type.cast(actor);
    }
}
