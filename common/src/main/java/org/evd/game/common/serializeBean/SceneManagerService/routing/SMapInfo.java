package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** 玩家当前地图或目标地图信息。sceneId 为 0 表示尚未分配场景实例。 */
@SerializeClass
public final class SMapInfo implements ISerializable {
    private long sceneId;
    private int mapCfgId;
    private long groupId;

    public SMapInfo() {
    }

    public SMapInfo(long sceneId, int mapCfgId, long groupId) {
        this.sceneId = sceneId;
        this.mapCfgId = mapCfgId;
        this.groupId = groupId;
    }

    public SMapInfo(SMapInfo other) {
        this(other == null ? 0L : other.sceneId,
                other == null ? 0 : other.mapCfgId,
                other == null ? 0L : other.groupId);
    }

    public long getSceneId() {
        return sceneId;
    }

    public void setSceneId(long sceneId) {
        this.sceneId = sceneId;
    }

    public int getMapCfgId() {
        return mapCfgId;
    }

    public void setMapCfgId(int mapCfgId) {
        this.mapCfgId = mapCfgId;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public SMapKey toMapKey() {
        return new SMapKey(mapCfgId, groupId);
    }

    @Override
    public String toString() {
        return "SMapInfo{" +
                "sceneId=" + sceneId +
                ", mapCfgId=" + mapCfgId +
                ", groupId=" + groupId +
                '}';
    }
}
