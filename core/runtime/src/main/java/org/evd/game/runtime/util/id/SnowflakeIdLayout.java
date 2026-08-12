package org.evd.game.runtime.util.id;

/**
 * Snowflake ID 布局公共实现。
 *
 * <p>sign 和 version 固定占用 bit 63、bit 62~61。具体版本只需要声明各业务字段
 * 的 bit 数，本类统一计算 shift/mask，并完成参数校验、ID 拼接和字段解析。</p>
 */
public abstract class SnowflakeIdLayout {

    private static final int SIGN_BITS = 1;
    private static final int VERSION_BITS = 2;
    public static final int VERSION_SHIFT = Long.SIZE - SIGN_BITS - VERSION_BITS;
    private static final long VERSION_MASK = mask(VERSION_BITS);

    private final SnowflakeIdType type;
    private final int version;
    private final int sequenceBits;

    private final int epochSecondShift;
    private final int nodeShift;
    private final int playerServerShift;
    private final int platformShift;

    private final long maxPlatformId;
    private final long maxPlayerServerId;
    private final long maxNodeId;
    private final long maxEpochSecond;
    private final long maxSequence;

    private final int platformId;
    private final int playerServerId;
    private final int nodeId;

    protected SnowflakeIdLayout(SnowflakeIdType type, int version,
                                int platformBits, int playerServerBits, int nodeBits,
                                int epochSecondBits, int sequenceBits,
                                int platformId, int playerServerId, int nodeId) {
        requireRange("version", version, VERSION_MASK);
        requireTotalBits(type + " version " + version,
                platformBits + playerServerBits + nodeBits + epochSecondBits + sequenceBits);

        this.type = type;
        this.version = version;
        this.sequenceBits = sequenceBits;

        epochSecondShift = sequenceBits;
        nodeShift = epochSecondShift + epochSecondBits;
        playerServerShift = nodeShift + nodeBits;
        platformShift = playerServerShift + playerServerBits;

        maxPlatformId = mask(platformBits);
        maxPlayerServerId = mask(playerServerBits);
        maxNodeId = mask(nodeBits);
        maxEpochSecond = mask(epochSecondBits);
        maxSequence = mask(sequenceBits);

        requireRange("platformId", platformId, maxPlatformId);
        requireRange("playerServerId", playerServerId, maxPlayerServerId);
        requireRange("nodeId", nodeId, maxNodeId);
        this.platformId = platformId;
        this.playerServerId = playerServerId;
        this.nodeId = nodeId;
    }

    public final SnowflakeIdType type() {
        return type;
    }

    public final int version() {
        return version;
    }

    public final int sequenceBits() {
        return sequenceBits;
    }

    public final long sequenceMask() {
        return maxSequence;
    }

    public final long maxEpochSecond() {
        return maxEpochSecond;
    }

    /**
     * 根据公共运行状态生成下一个 ID。同步边界由 SnowflakeIdGenerator 统一保证。
     */
    final long createId(long nowEpochSecond, SnowflakeIdGenerator.GenerationState state) {
        if (nowEpochSecond < 0) {
            throw new IllegalStateException("current time is before snowflake epoch: " + nowEpochSecond);
        }
        if (state.lastObservedEpochSecond >= 0
                && nowEpochSecond < state.lastObservedEpochSecond) {
            throw new IllegalStateException("clock moved backwards: current=" + nowEpochSecond
                    + ", last=" + state.lastObservedEpochSecond);
        }
        state.lastObservedEpochSecond = nowEpochSecond;

        if (nowEpochSecond > state.lastEpochSecond) {
            state.lastEpochSecond = nowEpochSecond;
            state.sequence = 0;
        } else {
            state.sequence++;
            if (state.sequence > maxSequence) {
                state.sequence = 0;
                state.lastEpochSecond++;
            }
        }

        if (state.lastEpochSecond > maxEpochSecond) {
            throw new IllegalStateException("epochSecond overflow for snowflake version "
                    + version + ": " + state.lastEpochSecond);
        }

        long id = pack(state.lastEpochSecond, state.sequence);
        if (id <= 0) {
            throw new IllegalStateException("generated snowflake id must be positive: " + id);
        }
        return id;
    }

    public final long pack(long epochSecond, long sequence) {
        requireRange("epochSecond", epochSecond, maxEpochSecond);
        requireRange("sequence", sequence, maxSequence);
        return versionBits(version)
                | ((long) platformId << platformShift)
                | ((long) playerServerId << playerServerShift)
                | ((long) nodeId << nodeShift)
                | (epochSecond << epochSecondShift)
                | sequence;
    }

    public final int decodePlatformId(long id) {
        requireVersion(id);
        return (int) ((id >>> platformShift) & maxPlatformId);
    }

    public final int decodePlayerServerId(long id) {
        requireVersion(id);
        return (int) ((id >>> playerServerShift) & maxPlayerServerId);
    }

    public final int decodeNodeId(long id) {
        requireVersion(id);
        return (int) ((id >>> nodeShift) & maxNodeId);
    }

    public final long decodeEpochSecond(long id) {
        requireVersion(id);
        return (id >>> epochSecondShift) & maxEpochSecond;
    }

    public final long decodeSequence(long id) {
        requireVersion(id);
        return id & maxSequence;
    }

    public static int extractVersion(long id) {
        return (int) ((id >>> VERSION_SHIFT) & VERSION_MASK);
    }

    public static void requireSignBit(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("snowflake sign bit must be 0: " + id);
        }
    }

    private void requireVersion(long id) {
        requireSignBit(id);
        int actualVersion = extractVersion(id);
        if (actualVersion != version) {
            throw new IllegalArgumentException("expected snowflake version " + version
                    + ", actual: " + actualVersion);
        }
    }

    private static long versionBits(int version) {
        return (long) version << VERSION_SHIFT;
    }

    private static long mask(int bits) {
        if (bits <= 0 || bits >= Long.SIZE) {
            throw new IllegalArgumentException("bits must be between 1 and 63: " + bits);
        }
        return (1L << bits) - 1;
    }

    private static void requireRange(String field, long value, long maxValue) {
        if (value < 0 || value > maxValue) {
            throw new IllegalArgumentException(field + " out of range [0, " + maxValue + "]: " + value);
        }
    }

    private static void requireTotalBits(String layoutName, int layoutBits) {
        int totalBits = SIGN_BITS + VERSION_BITS + layoutBits;
        if (totalBits != Long.SIZE) {
            throw new IllegalArgumentException(layoutName + " total bits must be "
                    + Long.SIZE + ", actual: " + totalBits);
        }
    }
}
