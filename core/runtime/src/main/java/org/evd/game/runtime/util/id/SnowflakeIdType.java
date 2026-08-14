package org.evd.game.runtime.util.id;

/**
 * ID 所属的布局类型。
 *
 * <p>类型由游戏配置确定，不占用 ID bit；version 只表示对应类型内部的布局版本。</p>
 */
public enum SnowflakeIdType {
    ROLLING_SERVER,
    MULTI_NODE,
    MYSQL
}
