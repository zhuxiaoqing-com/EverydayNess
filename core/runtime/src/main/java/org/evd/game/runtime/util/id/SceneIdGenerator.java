package org.evd.game.runtime.util.id;

/**
 * version 1：场景 Snowflake ID 布局。
 *
 * <pre>
 * | 字段             | bit | 范围/说明                                      |
 * |------------------|-----|------------------------------------------------|
 * | sign             | 1   | 固定 0，生成的 ID 始终为正数                   |
 * | nodeId           | 11  | 0~2047，最多 2048 个 Node                     |
 * | epochSecond      | 29  | 使用统一 Snowflake 时间基准，约 17.0 年          |
 * | sequence         | 23  | 0~8388607，单 Node 每秒最多 8388608 个 ID      |
 * </pre>
 */
public final class SceneIdGenerator {
    private static final int NODE_BITS = 11;
    private static final int EPOCH_SECOND_BITS = 29;
    private static final int SEQUENCE_BITS = 23;
    private static final int NODE_SHIFT = SEQUENCE_BITS;
    private static final int EPOCH_SECOND_SHIFT = NODE_BITS + SEQUENCE_BITS;
    private static final long MAX_NODE_ID = (1L << NODE_BITS) - 1;
    private static final long MAX_EPOCH_SECOND = (1L << EPOCH_SECOND_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static int nodeId;
    private static boolean initialized;
    private static long lastEpochSecond = -1L;
    private static long sequence;

    private SceneIdGenerator() {
    }

    public static synchronized void init(int nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("scene id nodeId out of range: " + nodeId);
        }
        SceneIdGenerator.nodeId = nodeId;
        lastEpochSecond = -1L;
        sequence = 0L;
        initialized = true;
    }

    /** 创建下一个场景 ID。 */
    public static synchronized long nextId() {
        if (!initialized) {
            throw new IllegalStateException("SceneIdGenerator is not initialized");
        }
        long nowEpochSecond = currentEpochSecond();
        if (nowEpochSecond < 0L) {
            throw new IllegalStateException("scene id current time is before epoch: " + nowEpochSecond);
        }

        if (nowEpochSecond > lastEpochSecond) {
            lastEpochSecond = nowEpochSecond;
            sequence = 0L;
        } else {
            sequence++;
            if (sequence > MAX_SEQUENCE) {
                sequence = 0L;
                lastEpochSecond++;
            }
        }

        if (lastEpochSecond > MAX_EPOCH_SECOND) {
            throw new IllegalStateException("scene id epochSecond overflow: " + lastEpochSecond);
        }
        return (lastEpochSecond << EPOCH_SECOND_SHIFT)
                | ((long) nodeId << NODE_SHIFT)
                | sequence;
    }

    private static long currentEpochSecond() {
        return Math.floorDiv(System.currentTimeMillis() - SnowflakeIdLayout.EPOCH_MILLIS, 1_000L);
    }
}
