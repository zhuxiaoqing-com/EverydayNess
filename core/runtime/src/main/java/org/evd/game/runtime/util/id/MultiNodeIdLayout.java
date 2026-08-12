package org.evd.game.runtime.util.id;

/**
 * version 1：多 Node Snowflake ID 布局。
 *
 * <pre>
 * | 字段             | bit | 范围/说明                                      |
 * |------------------|-----|------------------------------------------------|
 * | sign             | 1   | 固定 0，生成的 ID 始终为正数                   |
 * | version          | 2   | 固定 1，位于 bit 62~61                         |
 * | platformId       | 6   | 0~63                                           |
 * | playerServerId   | 10  | 0~1023                                         |
 * | nodeId           | 6   | 0~63，最多 64 个 Node                          |
 * | epochSecond      | 29  | 0~536870911，约 17.02 年                       |
 * | sequence         | 10  | 0~1023，单 Node 每秒最多 1024 个 ID             |
 * </pre>
 */
public final class MultiNodeIdLayout implements SnowflakeIdLayout {

    public static final int VERSION = 1;

    private static final int PLATFORM_BITS = 6;
    private static final int PLAYER_SERVER_BITS = 10;
    private static final int NODE_BITS = 6;
    private static final int EPOCH_SECOND_BITS = 29;
    private static final int SEQUENCE_BITS = 10;

    private static final int SEQUENCE_SHIFT = 0;
    private static final int EPOCH_SECOND_SHIFT = SEQUENCE_SHIFT + SEQUENCE_BITS;
    private static final int NODE_SHIFT = EPOCH_SECOND_SHIFT + EPOCH_SECOND_BITS;
    private static final int PLAYER_SERVER_SHIFT = NODE_SHIFT + NODE_BITS;
    private static final int PLATFORM_SHIFT = PLAYER_SERVER_SHIFT + PLAYER_SERVER_BITS;

    private static final long MAX_PLATFORM_ID = SnowflakeIdLayout.mask(PLATFORM_BITS);
    private static final long MAX_PLAYER_SERVER_ID = SnowflakeIdLayout.mask(PLAYER_SERVER_BITS);
    private static final long MAX_NODE_ID = SnowflakeIdLayout.mask(NODE_BITS);
    private static final long MAX_EPOCH_SECOND = SnowflakeIdLayout.mask(EPOCH_SECOND_BITS);
    private static final long MAX_SEQUENCE = SnowflakeIdLayout.mask(SEQUENCE_BITS);

    static {
        SnowflakeIdLayout.requireTotalBits("MultiNodeIdLayout",
                PLATFORM_BITS + PLAYER_SERVER_BITS + NODE_BITS + EPOCH_SECOND_BITS + SEQUENCE_BITS);
    }

    private final int platformId;
    private final int playerServerId;
    private final int nodeId;

    public MultiNodeIdLayout(int platformId, int playerServerId, int nodeId) {
        SnowflakeIdLayout.requireRange("platformId", platformId, MAX_PLATFORM_ID);
        SnowflakeIdLayout.requireRange("playerServerId", playerServerId, MAX_PLAYER_SERVER_ID);
        SnowflakeIdLayout.requireRange("nodeId", nodeId, MAX_NODE_ID);
        this.platformId = platformId;
        this.playerServerId = playerServerId;
        this.nodeId = nodeId;
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public int sequenceBits() {
        return SEQUENCE_BITS;
    }

    @Override
    public long maxEpochSecond() {
        return MAX_EPOCH_SECOND;
    }

    @Override
    public long pack(long epochSecond, long sequence) {
        SnowflakeIdLayout.requireRange("epochSecond", epochSecond, MAX_EPOCH_SECOND);
        SnowflakeIdLayout.requireRange("sequence", sequence, MAX_SEQUENCE);
        return SnowflakeIdLayout.versionBits(VERSION)
                | ((long) platformId << PLATFORM_SHIFT)
                | ((long) playerServerId << PLAYER_SERVER_SHIFT)
                | ((long) nodeId << NODE_SHIFT)
                | (epochSecond << EPOCH_SECOND_SHIFT)
                | sequence;
    }

    @Override
    public int decodePlatformId(long id) {
        requireVersion(id);
        return (int) ((id >>> PLATFORM_SHIFT) & MAX_PLATFORM_ID);
    }

    @Override
    public int decodePlayerServerId(long id) {
        requireVersion(id);
        return (int) ((id >>> PLAYER_SERVER_SHIFT) & MAX_PLAYER_SERVER_ID);
    }

    @Override
    public int decodeNodeId(long id) {
        requireVersion(id);
        return (int) ((id >>> NODE_SHIFT) & MAX_NODE_ID);
    }

    @Override
    public long decodeEpochSecond(long id) {
        requireVersion(id);
        return (id >>> EPOCH_SECOND_SHIFT) & MAX_EPOCH_SECOND;
    }

    @Override
    public long decodeSequence(long id) {
        requireVersion(id);
        return id & MAX_SEQUENCE;
    }

    private static void requireVersion(long id) {
        SnowflakeIdLayout.requireSignBit(id);
        int actualVersion = SnowflakeIdLayout.extractVersion(id);
        if (actualVersion != VERSION) {
            throw new IllegalArgumentException("expected snowflake version " + VERSION + ", actual: " + actualVersion);
        }
    }

}
