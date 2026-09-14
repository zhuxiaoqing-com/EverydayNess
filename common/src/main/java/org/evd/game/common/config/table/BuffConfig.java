package org.evd.game.common.config.table;

import lombok.Getter;
import org.evd.game.annotation.config.Config;

/** 场景 Buff 配置；技能的 effectIds 引用这里的 id。 */
@Config(file = "buff.csv", keys = {"id"})
@Getter
public class BuffConfig {
    /** Buff 配置 ID。 */
    private int id;
    /** Buff 名称。 */
    private String name;
    /** 持续时间，单位为毫秒。小于等于 0 表示不限制时间。 */
    private long durationMs;
    /** 攻击力变化。 */
    private int attackDelta;
    /** 防御力变化。 */
    private int defenseDelta;
    /** 每次触发造成的伤害。 */
    private int damagePerTick;
    /** 持续伤害触发间隔，单位为毫秒。 */
    private long tickIntervalMs;
}
