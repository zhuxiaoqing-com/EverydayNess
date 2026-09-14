package org.evd.game.common.config.table;

import lombok.Getter;

import java.util.List;
import org.evd.game.annotation.config.Config;

/** 地图基础配置。 */
@Config(file = "map.csv", keys = {"mapCfgId"})
@Getter
@SuppressWarnings("unused")
public class MapConfig {
    /** 地图配置 ID。 */
    private int mapCfgId;
    /** 地图名称。 */
    private String name;
    /** 地图 Deal 类型值。 */
    private int type;
    /** 地图人数限制。 */
    private int playerLimit;
    /** 地图创建时自动生成的怪物配置 ID 列表，使用 # 分隔。 */
    private List<Integer> monsterIds;
}
