package org.evd.game.runtime.call;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.annotation.serialize.SerializeIgnore;
import org.evd.game.base.ISerializable;

@SerializeClass
public abstract class CallBase implements ISerializable {
    /** ------------从哪来-------------*/
    public CallPoint from;
    /** ------------到哪去-------------*/
    public CallPoint to;
    /** 请求后回调contextid */
    public long id;
    /** 运行时入站 Session 标识，仅用于本进程内分发，不参与序列化。 */
    @SerializeIgnore
    private transient long inboundSessionId = -1L;

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

    public long getInboundSessionId() {
        return inboundSessionId;
    }

    public void setInboundSessionId(long inboundSessionId) {
        this.inboundSessionId = inboundSessionId;
    }

}
