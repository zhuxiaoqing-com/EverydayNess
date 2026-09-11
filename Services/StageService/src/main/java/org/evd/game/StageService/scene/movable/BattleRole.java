package org.evd.game.StageService.scene.movable;

import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;

/** BattleScene 中的实际玩家角色，保存场景使用的基础数据和原始地图数据。 */
public final class BattleRole {
    private final long playerId;
    private final String name;
    private final int level;
    private final SPlayerMapData playerMapData;

    public BattleRole(SPlayerMapData playerMapData) {
        if (playerMapData == null || playerMapData.getPlayerId() <= 0L) {
            throw new IllegalArgumentException("BattleRole 玩家数据非法");
        }
        this.playerId = playerMapData.getPlayerId();
        this.name = playerMapData.getName();
        this.level = playerMapData.getLevel();
        this.playerMapData = new SPlayerMapData(playerMapData);
    }

    public long getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public SPlayerMapData getPlayerMapData() {
        return playerMapData;
    }

}
