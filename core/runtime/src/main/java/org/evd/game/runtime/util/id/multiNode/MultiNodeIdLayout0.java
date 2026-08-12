package org.evd.game.runtime.util.id.multiNode;

import org.evd.game.runtime.util.id.SnowflakeIdLayout;
import org.evd.game.runtime.util.id.SnowflakeIdType;

/**
 * version 0：多 Node Snowflake ID 布局。
 *
 * <pre>
 * | 字段             | bit | 范围/说明                                      |
 * |------------------|-----|------------------------------------------------|
 * | sign             | 1   | 固定 0，生成的 ID 始终为正数                   |
 * | version          | 2   | 固定 0，位于 bit 62~61                         |
 * | platformId       | 6   | 0~63                                           |
 * | playerServerId   | 10  | 0~1023                                         |
 * | nodeId           | 6   | 0~63，最多 64 个 Node                          |
 * | epochSecond      | 29  | 0~536870911，约 17.02 年                       |
 * | sequence         | 10  | 0~1023，单 Node 每秒最多 1024 个 ID             |
 * </pre>
 */
public final class MultiNodeIdLayout0 extends SnowflakeIdLayout {

    public static final int VERSION = 0;

    private static final int PLATFORM_BITS = 6;
    private static final int PLAYER_SERVER_BITS = 10;
    private static final int NODE_BITS = 6;
    private static final int EPOCH_SECOND_BITS = 29;
    private static final int SEQUENCE_BITS = 10;

    public MultiNodeIdLayout0(int platformId, int playerServerId, int nodeId) {
        super(SnowflakeIdType.MULTI_NODE, VERSION,
                PLATFORM_BITS, PLAYER_SERVER_BITS, NODE_BITS, EPOCH_SECOND_BITS, SEQUENCE_BITS,
                platformId, playerServerId, nodeId);
    }
}
