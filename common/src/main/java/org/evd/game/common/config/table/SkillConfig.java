package org.evd.game.common.config.table;

import org.evd.game.annotation.config.Config;
import lombok.Getter;

import java.util.List;

/** 技能等级配置，同一个技能可以配置多条等级数据。 */
@Config(file = "skill.csv", keys = {"skillId", "level"})
@Getter
public class SkillConfig {
    /** 技能配置 ID。 */
    private int skillId;
    /** 技能等级。 */
    private int level;
    /** 技能名称。 */
    private String name;
    /** 技能伤害。 */
    private int damage;
    /** 冷却时间，单位为毫秒。 */
    private long cooldownMs;
    /** 消耗的魔法值。 */
    private int consumeMp;
    /** 技能效果 ID 列表，使用 # 分隔。 */
    private List<Integer> effectIds;
}
