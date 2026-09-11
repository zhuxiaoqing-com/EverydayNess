package org.evd.game.common.serializeBean.MatchService.match;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** 匹配补位机器人信息，由场景侧根据统一进图参数创建。 */
@SerializeClass
public final class SMatchRobot implements ISerializable {
    private long playerId;
    private int camp;
    private int duty;
    private String name;

    public SMatchRobot() {
    }

    public SMatchRobot(long playerId, int camp, int duty, String name) {
        this.playerId = playerId;
        this.camp = camp;
        this.duty = duty;
        this.name = name;
    }

    public SMatchRobot(SMatchRobot other) {
        this(other == null ? 0L : other.playerId,
                other == null ? 0 : other.camp,
                other == null ? 0 : other.duty,
                other == null ? null : other.name);
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getCamp() {
        return camp;
    }

    public void setCamp(int camp) {
        this.camp = camp;
    }

    public int getDuty() {
        return duty;
    }

    public void setDuty(int duty) {
        this.duty = duty;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
