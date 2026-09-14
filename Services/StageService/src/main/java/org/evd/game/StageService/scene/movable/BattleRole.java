package org.evd.game.StageService.scene.movable;

import org.evd.game.StageService.scene.battle.BattleUnit;
import org.evd.game.StageService.scene.logic.BuffLogic;
import org.evd.game.StageService.scene.logic.SkillLogic;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;

/** BattleScene 中的实际玩家角色，保存场景使用的基础数据和原始地图数据。 */
public final class BattleRole extends BattleUnit {
    private final SPlayerMapData playerMapData;

    public BattleRole(SPlayerMapData playerMapData, BuffLogic buffLogic, SkillLogic skillLogic) {
        super(require(playerMapData).getPlayerId(), playerMapData.getName(), playerMapData.getLevel(),
                100, 10, 0, 100, buffLogic, skillLogic);
        this.playerMapData = new SPlayerMapData(require(playerMapData));
    }

    private static SPlayerMapData require(SPlayerMapData playerMapData) {
        if (playerMapData == null || playerMapData.getPlayerId() <= 0L) {
            throw new IllegalArgumentException("BattleRole 玩家数据非法");
        }
        return playerMapData;
    }

    public long getPlayerId() {
        return getId();
    }

    public String getName() {
        return super.getName();
    }

    public int getLevel() {
        return super.getLevel();
    }

    public SPlayerMapData getPlayerMapData() {
        return playerMapData;
    }

}
