package org.evd.game.runtime.actor;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.evd.game.annotation.ActorType;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

import java.util.Objects;

@SerializeClass
public class ActorId implements ISerializable {
    @SerializeField
    private int typeCode;
    @SerializeField
    private long uniqueId;

    public ActorId() {
    }

    public ActorId(ActorType type, long uniqueId) {
        this(type.getCode(), uniqueId);
    }

    public ActorId(int typeCode, long uniqueId) {
        this.typeCode = typeCode;
        this.uniqueId = uniqueId;
    }

    public ActorId(ActorId other) {
        this(other.typeCode, other.uniqueId);
    }

    public static ActorId player(long uniqueId) {
        return new ActorId(ActorType.PLAYER, uniqueId);
    }

    public static ActorId map(long uniqueId) {
        return new ActorId(ActorType.MAP, uniqueId);
    }

    public static ActorId gate(long uniqueId) {
        return new ActorId(ActorType.GATE, uniqueId);
    }

    public static ActorId mapPlayer(long uniqueId) {
        return new ActorId(ActorType.MAP_PLAYER, uniqueId);
    }

    public ActorType getType() {
        return ActorType.fromCode(typeCode);
    }

    public void setType(ActorType type) {
        this.typeCode = type.getCode();
    }

    public long getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(long uniqueId) {
        this.uniqueId = uniqueId;
    }

    public int getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(int typeCode) {
        this.typeCode = typeCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActorId that)) {
            return false;
        }
        return typeCode == that.typeCode && uniqueId == that.uniqueId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeCode, uniqueId);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("type", getType())
                .append("uniqueId", getUniqueId())
                .toString();
    }
}
