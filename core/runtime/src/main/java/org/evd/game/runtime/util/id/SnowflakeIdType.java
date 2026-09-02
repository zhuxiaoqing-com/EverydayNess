package org.evd.game.runtime.util.id;

/**
 * ID 所属的布局类型。
 *
 * <p>类型由游戏配置确定，不占用 ID bit；version 只表示对应类型内部的布局版本。</p>
 */
public enum SnowflakeIdType {
    /** 滚服部署使用的 Snowflake ID 布局。 */
    ROLLING_SERVER,
    /** 多节点部署使用的 Snowflake ID 布局。 */
    MULTI_NODE,
    /** 基于 MySQL 号段分配器生成 ID 的布局。 */
    MYSQL
}
