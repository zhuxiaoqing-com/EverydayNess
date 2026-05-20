package org.evd.game.runtime.actor;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

@SerializeClass
public class ActorAddress implements ISerializable {
    @SerializeField
    private CallPoint callPoint;
    @SerializeField
    private long ownerInstanceId;
    @SerializeField
    private long mailBoxInstanceId;

    public ActorAddress() {
    }

    public ActorAddress(CallPoint callPoint, long ownerInstanceId, long mailBoxInstanceId) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
        this.ownerInstanceId = ownerInstanceId;
        this.mailBoxInstanceId = mailBoxInstanceId;
    }

    public ActorAddress(ActorAddress other) {
        this(other.callPoint, other.ownerInstanceId, other.mailBoxInstanceId);
    }

    public CallPoint getCallPoint() {
        return callPoint == null ? null : new CallPoint(callPoint);
    }

    public void setCallPoint(CallPoint callPoint) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
    }

    public long getOwnerInstanceId() {
        return ownerInstanceId;
    }

    public void setOwnerInstanceId(long ownerInstanceId) {
        this.ownerInstanceId = ownerInstanceId;
    }

    public long getMailBoxInstanceId() {
        return mailBoxInstanceId;
    }

    public void setMailBoxInstanceId(long mailBoxInstanceId) {
        this.mailBoxInstanceId = mailBoxInstanceId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("callPoint", callPoint)
                .append("ownerInstanceId", ownerInstanceId)
                .append("mailBoxInstanceId", mailBoxInstanceId)
                .toString();
    }
}
