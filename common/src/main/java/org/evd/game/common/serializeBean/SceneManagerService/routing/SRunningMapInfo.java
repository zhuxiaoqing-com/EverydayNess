package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** 运行中地图的通用信息，供需要复用已有地图的业务查询。 */
@SerializeClass
public final class SRunningMapInfo implements ISerializable {
    private SMapInfo mapInfo;
    private int hostRoleNum;
    private int guestRoleNum;
    private int sideLimit;

    public SRunningMapInfo() {
    }

    public SRunningMapInfo(SMapInfo mapInfo, int hostRoleNum, int guestRoleNum, int sideLimit) {
        this.mapInfo = mapInfo == null ? null : new SMapInfo(mapInfo);
        this.hostRoleNum = hostRoleNum;
        this.guestRoleNum = guestRoleNum;
        this.sideLimit = sideLimit;
    }

    public SRunningMapInfo(SRunningMapInfo other) {
        this(other == null ? null : other.mapInfo,
                other == null ? 0 : other.hostRoleNum,
                other == null ? 0 : other.guestRoleNum,
                other == null ? 0 : other.sideLimit);
    }

    public SMapInfo getMapInfo() {
        return mapInfo;
    }

    public void setMapInfo(SMapInfo mapInfo) {
        this.mapInfo = mapInfo == null ? null : new SMapInfo(mapInfo);
    }

    public int getHostRoleNum() {
        return hostRoleNum;
    }

    public void setHostRoleNum(int hostRoleNum) {
        this.hostRoleNum = hostRoleNum;
    }

    public int getGuestRoleNum() {
        return guestRoleNum;
    }

    public void setGuestRoleNum(int guestRoleNum) {
        this.guestRoleNum = guestRoleNum;
    }

    public int getSideLimit() {
        return sideLimit;
    }

    public void setSideLimit(int sideLimit) {
        this.sideLimit = sideLimit;
    }
}
