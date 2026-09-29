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
    /** 创建地图后进入候场的等待时间，单位秒。 */
    private int waitTime;
    /** 候场结束后的开战倒计时，单位秒。 */
    private int countTime;
    /** 战斗时长，单位秒；为 0 时不按时长自动结束。 */
    private int validTime;
    /** 战斗结束后销毁地图的等待时间，单位秒；为 0 时下一次 tick 销毁。 */
    private int destroyDelay;
}
