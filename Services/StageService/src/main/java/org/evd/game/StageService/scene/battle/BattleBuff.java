package org.evd.game.StageService.scene.battle;

import org.evd.game.common.config.table.BuffConfig;

/** 场景中的一个 Buff 实例。Buff 生命周期由所在场景的 tick 驱动。 */
public final class BattleBuff {
    private final BuffConfig config;
    private long expireAt;
    private long nextTickAt;

    public BattleBuff(BuffConfig config, long now) {
        this.config = config;
        this.expireAt = config.getDurationMs() <= 0L
                ? Long.MAX_VALUE : now + config.getDurationMs();
        this.nextTickAt = config.getTickIntervalMs() <= 0L
                ? Long.MAX_VALUE : now + config.getTickIntervalMs();
    }

    public static BattleBuff create(BuffConfig config, long now) {
        return new BattleBuff(config, now);
    }

    public int getAttackDelta() {
        return config.getAttackDelta();
    }

    public int getDefenseDelta() {
        return config.getDefenseDelta();
    }

    public boolean update(BattleUnit target, long now) {
        if (now >= expireAt) {
            return false;
        }
        long tickInterval = config.getTickIntervalMs();
        if (config.getDamagePerTick() > 0 && tickInterval > 0 && now >= nextTickAt) {
            target.takeDamage(config.getDamagePerTick());
            nextTickAt = now + tickInterval;
        }
        return now < expireAt;
    }

    public void refresh(long now) {
        expireAt = config.getDurationMs() <= 0L
                ? Long.MAX_VALUE : now + config.getDurationMs();
        nextTickAt = config.getTickIntervalMs() <= 0L
                ? Long.MAX_VALUE : now + config.getTickIntervalMs();
    }
}
