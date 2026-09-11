package org.evd.game.common.serializeBean.MatchService.match;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.ArrayList;
import java.util.List;

/** 一次匹配进图携带的统一扩展参数。 */
@SerializeClass
public final class SMatchEnterParams implements ISerializable {
    private int camp;
    private int dungeonDuty;
    private List<SMatchRobot> robots = new ArrayList<>();

    public SMatchEnterParams() {
    }

    public SMatchEnterParams(int camp, int dungeonDuty, List<SMatchRobot> robots) {
        this.camp = camp;
        this.dungeonDuty = dungeonDuty;
        if (robots != null) {
            for (SMatchRobot robot : robots) {
                this.robots.add(new SMatchRobot(robot));
            }
        }
    }

    public SMatchEnterParams(SMatchEnterParams other) {
        this(other == null ? 0 : other.camp,
                other == null ? 0 : other.dungeonDuty,
                other == null ? null : other.robots);
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

    public List<SMatchRobot> getRobots() {
        return robots;
    }

    public void setRobots(List<SMatchRobot> robots) {
        this.robots = new ArrayList<>();
        if (robots != null) {
            for (SMatchRobot robot : robots) {
                this.robots.add(new SMatchRobot(robot));
            }
        }
    }
}
