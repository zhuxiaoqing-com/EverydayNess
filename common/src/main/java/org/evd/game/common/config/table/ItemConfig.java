package org.evd.game.common.config.table;

import org.evd.game.annotation.config.Column;
import org.evd.game.annotation.config.Config;
import org.evd.game.annotation.config.Converter;
import org.evd.game.common.config.converter.TrimmedStringConverter;
import lombok.Getter;

/** 道具基础配置。 */
@Config(file = "item.csv", keys = {"id"})
@Getter
public class ItemConfig {
    /** 道具配置 ID。 */
    private int id;
    /** 道具名称。 */
    @Column("item_name")
    private String name;
    /** 道具类型。 */
    private int type;
    /** 道具品质。 */
    private int quality;
    /** 最大堆叠数量。 */
    private int maxStack;
    /** 出售价格。 */
    private int sellPrice;
    /** 道具描述。 */
    @Converter(TrimmedStringConverter.class)
    private String description;
}
