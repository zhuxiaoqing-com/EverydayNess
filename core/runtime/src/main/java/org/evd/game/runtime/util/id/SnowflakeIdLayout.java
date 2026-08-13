package org.evd.game.runtime.util.id;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Snowflake ID 布局公共实现。
 *
 * <p>sign、version 和布局字段的位宽由具体布局声明，本类统一计算 shift/mask，
 * 并完成参数校验、ID 拼接和字段解析。</p>
 */
public abstract class SnowflakeIdLayout {

    private static final Map<IDEnum, GenerationState> GENERATION_STATE_MAP = new HashMap<>();
    private static final Object GENERATION_LOCK = new Object();

    static {
        for (IDEnum idEnum : IDEnum.values()) {
            GENERATION_STATE_MAP.putIfAbsent(idEnum, new GenerationState());
        }
    }
    /**
     * 每个 ID 类型独立维护生成水位。
     */
    static final class GenerationState {
        /** 实际写入 ID 的逻辑时间，物理时间落后时继续借秒生成。 */
        long lastEpochSecond = -1;
        long sequence;
    }

    @FunctionalInterface
    public interface LayoutFactory {
        SnowflakeIdLayout create(int platformId, int playerServerId, int nodeId);
    }

    /** 所有 Snowflake 版本统一使用的起始时间：2024-01-01 00:00:00 UTC+8。 */
    public static final long EPOCH_MILLIS = LocalDate.of(2024, 1, 1)
            .atStartOfDay()
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli();

    private static long currentEpochSecond() {
        return Math.floorDiv(System.currentTimeMillis() - EPOCH_MILLIS, 1000);
    }

    private static final int SIGN_BITS = 1;
    private final SnowflakeIdType type;
    private final int version;
    private final int versionShift;
    private final long versionMask;
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

    protected SnowflakeIdLayout(SnowflakeIdType type, int version, int versionBits,
                                int platformBits, int playerServerBits, int nodeBits,
                                int epochSecondBits, int sequenceBits,
                                int platformId, int playerServerId, int nodeId) {
        requireBitCount("versionBits", versionBits);
        long versionMask = mask(versionBits);
        requireTotalBits(type + " version " + version,
                versionBits, platformBits + playerServerBits + nodeBits + epochSecondBits + sequenceBits);

        this.type = type;
        this.version = version;
        this.versionShift = Long.SIZE - SIGN_BITS - versionBits;
        this.versionMask = versionMask;
        this.sequenceBits = sequenceBits;
        requireRange("version", version, versionMask);

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

    /**
     * 创建当前已初始化布局的下一个 ID。
     */
    final long createId(IDEnum idEnum) {
        if (idEnum == null) {
            throw new IllegalArgumentException("idEnum is required");
        }
        GenerationState state = GENERATION_STATE_MAP.get(idEnum);
        if (state == null) {
            throw new IllegalStateException("snowflake generation state is not initialized: " + idEnum);
        }
        return nextIdLocked(state);
    }

    public int versionOf(long id) {
        requireSignBit(id);
        return (int) ((id >>> versionShift) & versionMask);
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

    public final long maxNodeId() {
        return maxNodeId;
    }

    public final long maxEpochSecond() {
        return maxEpochSecond;
    }

    private long nextIdLocked(GenerationState state) {
        long nowEpochSecond = currentEpochSecond();
        if (nowEpochSecond < 0) {
            throw new IllegalStateException("current time is before snowflake epoch: " + nowEpochSecond);
        }

        long time;
        long value;

        synchronized (GENERATION_LOCK) {
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
            time = state.lastEpochSecond;
            value = state.sequence;
        }

        if (time > maxEpochSecond) {
            throw new IllegalStateException("epochSecond overflow for snowflake version "
                    + version + ": " + time);
        }

        long id = pack(time, value);
        if (id <= 0) {
            throw new IllegalStateException("generated snowflake id must be positive: " + id);
        }
        return id;
    }

    public final long pack(long epochSecond, long sequence) {
        requireRange("epochSecond", epochSecond, maxEpochSecond);
        requireRange("sequence", sequence, maxSequence);
        return encodeVersion(version)
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

    public static void requireSignBit(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("snowflake sign bit must be 0: " + id);
        }
    }

    private void requireVersion(long id) {
        requireSignBit(id);
        int actualVersion = versionOf(id);
        if (actualVersion != version) {
            throw new IllegalArgumentException("expected snowflake version " + version
                    + ", actual: " + actualVersion);
        }
    }

    protected long encodeVersion(int version) {
        return (long) version << versionShift;
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

    private static void requireBitCount(String field, int bits) {
        if (bits <= 0 || bits >= Long.SIZE - SIGN_BITS) {
            throw new IllegalArgumentException(field + " must be between 1 and 62: " + bits);
        }
    }

    private static void requireTotalBits(String layoutName, int versionBits, int layoutBits) {
        int totalBits = SIGN_BITS + versionBits + layoutBits;
        if (totalBits > Long.SIZE) {
            throw new IllegalArgumentException(layoutName + " total bits must be at most "
                    + Long.SIZE + ", actual: " + totalBits);
        }
    }

    public final boolean matches(long id) {
        return id >= 0 && versionOf(id) == version;
    }

    public abstract SnowflakeIdLayout find(long id);
}
