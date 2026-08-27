package org.evd.game.common.config.table;

import org.evd.game.annotation.config.Config;
import lombok.Getter;

import java.util.Map;

/** 怪物基础属性和掉落配置。 */
@Config(file = "monster.csv", keys = {"id"})
@Getter
public class MonsterConfig {
    /** 怪物配置 ID。 */
    private int id;
    /** 怪物名称。 */
    private String name;
    /** 怪物等级。 */
    private int level;
    /** 生命值。 */
    private int hp;
    /** 攻击力。 */
    private int attack;
    /** 防御力。 */
    private int defense;
    /** 掉落道具及数量，格式为 itemId&count#itemId&count。 */
    private Map<Integer, Integer> dropItems;
}
