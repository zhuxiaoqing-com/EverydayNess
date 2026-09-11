package org.evd.game.common.serializeBean.MatchService.match;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.ArrayList;
import java.util.List;

/** 多人匹配场景创建所需的完整参赛名单。 */
@SerializeClass
public final class SMatchSceneCreateParams implements ISerializable {
    private List<SMatchScenePlayer> players = new ArrayList<>();
    private List<SMatchRobot> robots = new ArrayList<>();

    public SMatchSceneCreateParams() {
    }

    public SMatchSceneCreateParams(List<SMatchScenePlayer> players, List<SMatchRobot> robots) {
        setPlayers(players);
        setRobots(robots);
    }

    public SMatchSceneCreateParams(SMatchSceneCreateParams other) {
        this(other == null ? null : other.players, other == null ? null : other.robots);
    }

    public List<SMatchScenePlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<SMatchScenePlayer> players) {
        this.players = new ArrayList<>();
        if (players != null) {
            for (SMatchScenePlayer player : players) {
                this.players.add(new SMatchScenePlayer(player));
            }
        }
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
