package org.evd.game.runtime.actor;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

import java.util.Objects;

@SerializeClass
public class ActorAddress implements ISerializable {
    @SerializeField
    private CallPoint callPoint;
    @SerializeField
    private long mailBoxEpoch;

    public ActorAddress() {
    }

    public ActorAddress(CallPoint callPoint, long mailBoxEpoch) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
        this.mailBoxEpoch = mailBoxEpoch;
    }

    public ActorAddress(ActorAddress other) {
        this(other.callPoint, other.mailBoxEpoch);
    }

    public CallPoint getCallPoint() {
        return callPoint == null ? null : new CallPoint(callPoint);
    }

    public void setCallPoint(CallPoint callPoint) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
    }

    public long getMailBoxEpoch() {
        return mailBoxEpoch;
    }

    public void setMailBoxEpoch(long mailBoxEpoch) {
        this.mailBoxEpoch = mailBoxEpoch;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("callPoint", callPoint)
                .append("mailBoxEpoch", mailBoxEpoch)
                .toString();
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ActorAddress that = (ActorAddress) o;
        return mailBoxEpoch == that.mailBoxEpoch && Objects.equals(callPoint, that.callPoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callPoint, mailBoxEpoch);
    }
}
