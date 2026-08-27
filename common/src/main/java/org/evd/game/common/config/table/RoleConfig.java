package org.evd.game.common.config.table;

import org.evd.game.annotation.config.Column;
import org.evd.game.annotation.config.Config;

import org.evd.game.common.bean.ItemRef;
import lombok.Getter;

import java.util.List;

/** 角色基础属性配置。 */
@Config(file = "role.csv", keys = {"id"})
@Getter
public class RoleConfig {
    /** 角色配置 ID。 */
    private int id;
    /** 角色名称。 */
    @Column("role_name")
    private String name;
    /** 初始等级。 */
    private int level;
    /** 初始生命值。 */
    private int hp;
    /** 初始攻击力。 */
    private int attack;
    /** 初始防御力。 */
    private int defense;
    /** 初始赠送道具及数量，格式为 itemId&count#itemId&count。 */
    private List<ItemRef> initialItems;
}
