package org.evd.game.runtime.actor;

import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.mailbox.MailBoxComponent;
import org.evd.game.runtime.mailbox.MailBoxType;

import java.util.HashMap;
import java.util.Map;

public class ActorRegistry {
    public static final class Registration {
        private final Object actor;
        private final ActorExecutionMode executionMode;
        private final long ownerInstanceId;
        private final MailBoxComponent mailBoxComponent;

        private Registration(
                Object actor,
                ActorExecutionMode executionMode,
                long ownerInstanceId,
                MailBoxComponent mailBoxComponent
        ) {
            this.actor = actor;
            this.executionMode = executionMode;
            this.ownerInstanceId = ownerInstanceId;
            this.mailBoxComponent = mailBoxComponent;
        }

        public Object getActor() {
            return actor;
        }

        public ActorExecutionMode getExecutionMode() {
            return executionMode;
        }

        public long getOwnerInstanceId() {
            return ownerInstanceId;
        }

        public MailBoxComponent getMailBoxComponent() {
            return mailBoxComponent;
        }
    }

    private final Map<ActorId, Registration> actors = new HashMap<>();
    private final Map<Long, MailBoxComponent> mailBoxes = new HashMap<>();
    private long nextOwnerInstanceId = 1L;
    private long nextMailBoxInstanceId = 1L;

    public void register(ActorId actorId, Object actor, ActorExecutionMode executionMode) {
        long ownerInstanceId = nextOwnerInstanceId++;
        MailBoxComponent mailBoxComponent = new MailBoxComponent(
                actorId,
                actor,
                ownerInstanceId,
                nextMailBoxInstanceId++,
                executionMode == ActorExecutionMode.ORDERED ? MailBoxType.ORDERED : MailBoxType.UNORDERED);
        actors.put(new ActorId(actorId), new Registration(actor, executionMode, ownerInstanceId, mailBoxComponent));
        mailBoxes.put(ownerInstanceId, mailBoxComponent);
    }

    public void unregister(ActorId actorId) {
        Registration registration = actors.remove(actorId);
        if (registration == null) {
            return;
        }
        mailBoxes.remove(registration.getOwnerInstanceId());
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

    public MailBoxComponent getMailBox(long ownerInstanceId) {
        return mailBoxes.get(ownerInstanceId);
    }

    public boolean hasSameMailBoxInstance(long ownerInstanceId, long mailBoxInstanceId) {
        MailBoxComponent mailBoxComponent = mailBoxes.get(ownerInstanceId);
        return mailBoxComponent != null && mailBoxComponent.getInstanceId() == mailBoxInstanceId;
    }

    public <T> T require(ActorId actorId, Class<T> type) {
        Object actor = requireRegistration(actorId).getActor();
        if (!type.isInstance(actor)) {
            throw RpcCallException.actorTypeMismatch(actorId, type, actor.getClass());
        }
        return type.cast(actor);
    }
}
