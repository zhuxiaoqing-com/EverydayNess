package org.evd.game.StageService.scene.logic;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.scene.battle.BattleScene;
import org.evd.game.StageService.scene.battle.BattleBuff;
import org.evd.game.StageService.scene.battle.BattleUnit;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.config.table.BuffConfig;
import org.evd.game.common.config.table.BuffConfigs;

/** 负责 Buff 创建、刷新、属性计算和生命周期推进。 */
@Actor
@Slf4j
public final class BuffLogic {
    public boolean addBuff(BattleScene scene, long targetId, int buffId, long now) {
        if (scene == null) {
            return false;
        }
        BattleUnit target = scene.findUnit(targetId);
        return target != null && addBuff(target, buffId, now);
    }

    public boolean addBuff(BattleUnit target, int buffId, long now) {
        BattleBuff current = target.buffs().get(buffId);
        if (current != null) {
            current.refresh(now);
            return true;
        }
        BuffConfig config = BuffConfigs.get(buffId);
        if (config == null) {
            log.warn("找不到 Buff 配置: unitId={}, buffId={}", target.getId(), buffId);
            return false;
        }
        target.buffs().put(buffId, BattleBuff.create(config, now));
        return true;
    }

    public int getAttackDelta(BattleUnit target) {
        return target.buffs().values().stream().mapToInt(BattleBuff::getAttackDelta).sum();
    }

    public int getDefenseDelta(BattleUnit target) {
        return target.buffs().values().stream().mapToInt(BattleBuff::getDefenseDelta).sum();
    }

    public void update(BattleUnit target, long now) {
        target.buffs().values().removeIf(buff -> !buff.update(target, now));
    }
}
