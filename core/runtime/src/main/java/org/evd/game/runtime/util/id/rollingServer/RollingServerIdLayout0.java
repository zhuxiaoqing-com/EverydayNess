package org.evd.game.runtime.util.id.rollingServer;

import org.evd.game.runtime.util.id.SnowflakeIdLayout;
import org.evd.game.runtime.util.id.SnowflakeIdType;

/**
 * version 0：滚服 Snowflake ID 布局。
 *
 * <pre>
 * | 字段             | bit | 范围/说明                                      |
 * |------------------|-----|------------------------------------------------|
 * | sign             | 1   | 固定 0，生成的 ID 始终为正数                   |
 * | version          | 2   | 固定 0，位于 bit 62~61                         |
 * | platformId       | 8   | 0~255                                          |
 * | playerServerId   | 12  | 0~4095                                         |
 * | nodeId           | 2   | 0~3，最多 4 个 Node                            |
 * | epochSecond      | 29  | 0~536870911，约 17.02 年                       |
 * | sequence         | 10  | 0~1023，单 Node 每秒最多 1024 个 ID             |
 * </pre>
 */
public final class RollingServerIdLayout0 extends SnowflakeIdLayout {

    public static final int VERSION = 0;

    private static final int PLATFORM_BITS = 8;
    private static final int PLAYER_SERVER_BITS = 12;
    private static final int NODE_BITS = 2;
    private static final int EPOCH_SECOND_BITS = 29;
    private static final int SEQUENCE_BITS = 10;

    public RollingServerIdLayout0(int platformId, int playerServerId) {
        this(platformId, playerServerId, 0);
    }

    public RollingServerIdLayout0(int platformId, int playerServerId, int nodeId) {
        super(SnowflakeIdType.ROLLING_SERVER, VERSION,
                PLATFORM_BITS, PLAYER_SERVER_BITS, NODE_BITS, EPOCH_SECOND_BITS, SEQUENCE_BITS,
                platformId, playerServerId, nodeId);
    }
}
