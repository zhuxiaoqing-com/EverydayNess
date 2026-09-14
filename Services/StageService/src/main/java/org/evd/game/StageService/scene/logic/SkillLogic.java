package org.evd.game.StageService.scene.logic;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.scene.battle.BattleScene;
import org.evd.game.StageService.scene.battle.BattleUnit;
import org.evd.game.StageService.scene.movable.Monster;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.config.table.SkillConfig;
import org.evd.game.common.config.table.SkillConfigs;

/** 负责技能配置校验、消耗、冷却、伤害和技能效果执行。 */
@Actor
@Slf4j
public final class SkillLogic {
    public boolean useSkill(BattleScene scene, long casterId, int skillId, int level,
                            long targetId, long now) {
        if (scene == null) {
            return false;
        }
        BattleUnit caster = scene.findUnit(casterId);
        BattleUnit target = scene.findUnit(targetId);
        if (caster instanceof Monster monster && !monster.hasSkill(skillId)) {
            log.warn("BattleScene 怪物没有该技能: sceneId={}, monsterId={}, skillId={}",
                    scene.getSceneId(), casterId, skillId);
            return false;
        }
        return caster != null && target != null && useSkill(caster, target, skillId, level, now);
    }

    public boolean useSkill(BattleUnit caster, BattleUnit target, int skillId, int level, long now) {
        if (target == null || !caster.isAlive() || !target.isAlive()) {
            return false;
        }
        SkillConfig config = SkillConfigs.get(skillId, level);
        if (config == null) {
            log.warn("找不到技能配置: casterId={}, skillId={}, level={}", caster.getId(), skillId, level);
            return false;
        }
        long cooldownUntil = caster.skillCooldowns().getOrDefault(skillId, 0L);
        if (now < cooldownUntil || !caster.consumeMp(config.getConsumeMp())) {
            return false;
        }
        caster.skillCooldowns().put(skillId, now + Math.max(0L, config.getCooldownMs()));
        int damage = config.getDamage() > 0 ? config.getDamage() : caster.getAttack();
        target.takeDamage(Math.max(1, damage - target.getDefense()));
        if (config.getEffectIds() != null) {
            for (Integer effectId : config.getEffectIds()) {
                if (effectId != null && !target.addBuff(effectId, now)) {
                    log.warn("技能引用的 Buff 不存在: skillId={}, level={}, buffId={}",
                            skillId, level, effectId);
                }
            }
        }
        return true;
    }
}
