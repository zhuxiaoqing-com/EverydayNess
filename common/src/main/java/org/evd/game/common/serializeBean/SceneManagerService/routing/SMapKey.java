package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.Objects;

/** 由地图配置和功能分组共同确定一个场景。 */
@SerializeClass
public final class SMapKey implements ISerializable {
    private int mapCfgId;
    private long groupId;

    public SMapKey() {
    }

    public SMapKey(int mapCfgId, long groupId) {
        this.mapCfgId = mapCfgId;
        this.groupId = groupId;
    }

    public SMapKey(SMapInfo mapInfo) {
        this(mapInfo == null ? 0 : mapInfo.getMapCfgId(),
                mapInfo == null ? 0L : mapInfo.getGroupId());
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SMapKey mapKey)) {
            return false;
        }
        return mapCfgId == mapKey.mapCfgId && groupId == mapKey.groupId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapCfgId, groupId);
    }
}
