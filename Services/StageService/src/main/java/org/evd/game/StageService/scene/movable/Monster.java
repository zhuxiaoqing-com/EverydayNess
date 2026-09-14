package org.evd.game.StageService.scene.movable;

import org.evd.game.StageService.scene.battle.BattleUnit;
import org.evd.game.StageService.scene.logic.BuffLogic;
import org.evd.game.StageService.scene.logic.SkillLogic;
import org.evd.game.common.config.table.MonsterConfig;

import java.util.List;

/** 场景中的怪物实体。配置属性在生成时复制到实体，运行时状态不回写配置。 */
public final class Monster extends BattleUnit {
    private final int configId;
    private final List<Integer> skillIds;

    public Monster(long id, MonsterConfig config, BuffLogic buffLogic, SkillLogic skillLogic) {
        super(id, config.getName(), config.getLevel(), config.getHp(),
                config.getAttack(), config.getDefense(), 100, buffLogic, skillLogic);
        this.configId = config.getId();
        this.skillIds = config.getSkillIds() == null ? List.of() : List.copyOf(config.getSkillIds());
    }

    public int getConfigId() {
        return configId;
    }

    public boolean hasSkill(int skillId) {
        return skillIds.contains(skillId);
    }
}
