package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.actor.ActorId;

public final class MailBoxComponent {
    private final ActorId actorId;
    private final long epoch;
    private final MailBoxType mailBoxType;

    public MailBoxComponent(ActorId actorId, long epoch, MailBoxType mailBoxType) {
        this.actorId = new ActorId(actorId);
        this.epoch = epoch;
        this.mailBoxType = mailBoxType;
    }

    public ActorId getActorId() {
        return new ActorId(actorId);
    }

    public long getEpoch() {
        return epoch;
    }

    public MailBoxType getMailBoxType() {
        return mailBoxType;
    }
}
