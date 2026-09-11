package org.evd.game.common.serializeBean.MatchService.match;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

import java.util.ArrayList;
import java.util.List;

/** MatchService 保存的单个玩家匹配快照。 */
@SerializeClass
public final class SMatchPlayer implements ISerializable {
    private long playerId;
    private CallPoint playerService;
    private String name;
    private int level;
    private int headId;
    private int avatarId;
    /** 玩家愿意承担的职责，供启用职责规则的匹配模式使用。 */
    private List<Integer> dutyIds = new ArrayList<>();
    /** 职责匹配完成后记录的最终职责。 */
    private int selectedDuty;
    private boolean robot;

    public SMatchPlayer() {
    }

    public SMatchPlayer(long playerId, CallPoint playerService) {
        this.playerId = playerId;
        this.playerService = playerService == null ? null : new CallPoint(playerService);
    }

    public SMatchPlayer(long playerId, CallPoint playerService, List<Integer> dutyIds) {
        this(playerId, playerService);
        if (dutyIds != null) {
            this.dutyIds.addAll(dutyIds);
        }
    }

    public SMatchPlayer(SMatchPlayer other) {
        this(other == null ? 0L : other.playerId,
                other == null ? null : other.playerService,
                other == null ? null : other.dutyIds);
        if (other != null) {
            this.name = other.name;
            this.level = other.level;
            this.headId = other.headId;
            this.avatarId = other.avatarId;
            this.selectedDuty = other.selectedDuty;
            this.robot = other.robot;
        }
    }

    public static SMatchPlayer robot(long playerId, int duty, String name) {
        SMatchPlayer player = new SMatchPlayer();
        player.playerId = playerId;
        player.robot = true;
        player.name = name;
        player.selectedDuty = duty;
        if (duty > 0) player.dutyIds.add(duty);
        return player;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public CallPoint getPlayerService() {
        return playerService == null ? null : new CallPoint(playerService);
    }

    public void setPlayerService(CallPoint playerService) {
        this.playerService = playerService == null ? null : new CallPoint(playerService);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getHeadId() {
        return headId;
    }

    public void setHeadId(int headId) {
        this.headId = headId;
    }

    public int getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(int avatarId) {
        this.avatarId = avatarId;
    }

    public List<Integer> getDutyIds() {
        return dutyIds;
    }

    public void setDutyIds(List<Integer> dutyIds) {
        this.dutyIds = dutyIds == null ? new ArrayList<>() : dutyIds;
    }

    public int getSelectedDuty() {
        return selectedDuty;
    }

    public void setSelectedDuty(int selectedDuty) {
        this.selectedDuty = selectedDuty;
    }

    public boolean isRobot() {
        return robot;
    }

    public void setRobot(boolean robot) {
        this.robot = robot;
    }
}
