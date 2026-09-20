package org.evd.game.common.serializeBean.LocationService;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;

/** LocationService 批量恢复使用的 Actor 地址。 */
@SerializeClass
public final class SLocationAddress implements ISerializable {
    private ActorId actorId;
    private ActorAddress actorAddress;

    public SLocationAddress() {
    }

    public SLocationAddress(ActorId actorId, ActorAddress actorAddress) {
        this.actorId = actorId == null ? null : new ActorId(actorId);
        this.actorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
    }

    public SLocationAddress(SLocationAddress other) {
        this(other == null ? null : other.actorId,
                other == null ? null : other.actorAddress);
    }

    public ActorId getActorId() {
        return actorId;
    }

    public void setActorId(ActorId actorId) {
        this.actorId = actorId == null ? null : new ActorId(actorId);
    }

    public ActorAddress getActorAddress() {
        return actorAddress;
    }

    public void setActorAddress(ActorAddress actorAddress) {
        this.actorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
    }
}
