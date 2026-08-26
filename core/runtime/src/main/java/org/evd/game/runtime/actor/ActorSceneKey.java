package org.evd.game.runtime.actor;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.Objects;

/**
 * @author zhuxiaoqing
 * @Description: ActorSceneKey
 * @Date 2026/8/12 13:59
 **/
@SerializeClass
public class ActorSceneKey implements ISerializable {
    public int dungeonId; // 副本Id
    public long groupId; // 组id

    public int getDungeonId() {
        return dungeonId;
    }

    public void setDungeonId(int dungeonId) {
        this.dungeonId = dungeonId;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ActorSceneKey that = (ActorSceneKey) o;
        return dungeonId == that.dungeonId && groupId == that.groupId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dungeonId, groupId);
    }

    @Override
    public String toString() {
        return "ActorSceneKey{" +
                "dungeonId=" + dungeonId +
                ", groupId=" + groupId +
                '}';
    }
}
