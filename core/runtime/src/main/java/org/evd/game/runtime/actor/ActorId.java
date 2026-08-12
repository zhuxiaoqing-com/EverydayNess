package org.evd.game.runtime.actor;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.evd.game.annotation.ActorType;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.Objects;

@SerializeClass
public class ActorId implements ISerializable {
    /**
     * {@link ActorType}
     */
    private int typeCode;
    private long uniqueId;
    private ActorSceneKey sceneKey;

    public ActorId() {
    }

    public ActorId(ActorType type, long uniqueId) {
        this(type.getCode(), uniqueId);
    }

    public ActorId(int typeCode, long uniqueId) {
        this.typeCode = typeCode;
        this.uniqueId = uniqueId;
    }


    /**
     * 根据 typeCode 做判断
     */
    public boolean isCrossActor() {
        //if(typeCode == )
        return false;
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

    public ActorSceneKey getSceneKey() {
        return sceneKey;
    }

    public void setSceneKey(ActorSceneKey sceneKey) {
        this.sceneKey = sceneKey;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ActorId actorId = (ActorId) o;
        return typeCode == actorId.typeCode && uniqueId == actorId.uniqueId && Objects.equals(sceneKey, actorId.sceneKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeCode, uniqueId, sceneKey);
    }
}
