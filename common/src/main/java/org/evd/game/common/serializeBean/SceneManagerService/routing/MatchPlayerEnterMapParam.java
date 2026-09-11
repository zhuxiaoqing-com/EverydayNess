package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** 玩家进入匹配地图时携带的单人阵营和职责参数。 */
@SerializeClass
public final class MatchPlayerEnterMapParam implements ISerializable {
    private int camp;
    private int dungeonDuty;

    public MatchPlayerEnterMapParam() {
    }

    public MatchPlayerEnterMapParam(int camp, int dungeonDuty) {
        this.camp = camp;
        this.dungeonDuty = dungeonDuty;
    }

    public MatchPlayerEnterMapParam(MatchPlayerEnterMapParam other) {
        this(other == null ? 0 : other.camp,
                other == null ? 0 : other.dungeonDuty);
    }

    public int getCamp() {
        return camp;
    }

    public void setCamp(int camp) {
        this.camp = camp;
    }

    public int getDungeonDuty() {
        return dungeonDuty;
    }

    public void setDungeonDuty(int dungeonDuty) {
        this.dungeonDuty = dungeonDuty;
    }
}
