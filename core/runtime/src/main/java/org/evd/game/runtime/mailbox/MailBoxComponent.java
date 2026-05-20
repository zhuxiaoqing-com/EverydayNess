package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.actor.ActorId;

public final class MailBoxComponent {
    private final ActorId actorId;
    private final Object actor;
    private final long ownerInstanceId;
    private final long instanceId;
    private final MailBoxType mailBoxType;

    public MailBoxComponent(ActorId actorId, Object actor, long ownerInstanceId, long instanceId, MailBoxType mailBoxType) {
        this.actorId = new ActorId(actorId);
        this.actor = actor;
        this.ownerInstanceId = ownerInstanceId;
        this.instanceId = instanceId;
        this.mailBoxType = mailBoxType;
    }

    public ActorId getActorId() {
        return new ActorId(actorId);
    }

    public Object getActor() {
        return actor;
    }

    public long getOwnerInstanceId() {
        return ownerInstanceId;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public MailBoxType getMailBoxType() {
        return mailBoxType;
    }
}
