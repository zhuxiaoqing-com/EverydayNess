package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.actor.ActorId;

@SerializeClass
public abstract class CallBase implements ISerializable {
    /** ------------从哪来-------------*/
    @SerializeField
    public CallPoint from;
    /** ------------到哪去-------------*/
    @SerializeField
    public CallPoint to;
    /** 请求后回调contextid */
    @SerializeField
    public long id;
    /** actor 串行键，为 null 时表示普通 service 级调用 */
    @SerializeField
    public ActorId actorId;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public CallPoint getFrom() {
        return from;
    }

    public void setFrom(CallPoint from) {
        this.from = from;
    }

    public CallPoint getTo() {
        return to;
    }

    public void setTo(CallPoint to) {
        this.to = to;
    }

    public ActorId getActorId() {
        return actorId;
    }

    public void setActorId(ActorId actorId) {
        this.actorId = actorId;
    }
}
