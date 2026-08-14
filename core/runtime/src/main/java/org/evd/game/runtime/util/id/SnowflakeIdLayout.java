package org.evd.game.runtime.util.id;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Snowflake ID 布局公共实现。
 *
 * <p>本类只负责 Snowflake 的时间/序列生成，字段位移、ID 拼接和通用字段解析由
 * {@link IdLayout} 统一维护。</p>
 */
public abstract class SnowflakeIdLayout extends IdLayout {

    private static final Map<IDEnum, GenerationState> GENERATION_STATE_MAP = new HashMap<>();
    private static final Object GENERATION_LOCK = new Object();

    static {
        for (IDEnum idEnum : IDEnum.values()) {
            GENERATION_STATE_MAP.putIfAbsent(idEnum, new GenerationState());
        }
    }

    /** 每个 ID 类型独立维护生成水位。 */
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

    private final SnowflakeIdType type;
    private final int sequenceBits;
    private final int epochSecondShift;
    private final long maxEpochSecond;
    private final long maxSequence;

    protected SnowflakeIdLayout(SnowflakeIdType type, int version, int versionBits,
                                int platformBits, int playerServerBits, int nodeBits,
                                int epochSecondBits, int sequenceBits,
                                int platformId, int playerServerId, int nodeId) {
        super(0, version, platformBits, playerServerBits, nodeBits,
                epochSecondBits + sequenceBits, platformId, playerServerId, nodeId);
        if (type == null) {
            throw new IllegalArgumentException("snowflake type is required");
        }
        if (versionBits != 1) {
            throw new IllegalArgumentException("Snowflake versionBits must be 1: " + versionBits);
        }
        if (epochSecondBits <= 0 || sequenceBits <= 0) {
            throw new IllegalArgumentException("epochSecondBits and sequenceBits must be positive");
        }
        this.type = type;
        this.sequenceBits = sequenceBits;
        this.epochSecondShift = sequenceBits;
        this.maxEpochSecond = mask(epochSecondBits);
        this.maxSequence = mask(sequenceBits);
    }

    public final SnowflakeIdType type() {
        return type;
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

    @Override
    protected long nextIncrementId(IDEnum idEnum) {
        GenerationState state = GENERATION_STATE_MAP.get(idEnum);
        if (state == null) {
            throw new IllegalStateException("snowflake generation state is not initialized: " + idEnum);
        }
        return nextIdLocked(state);
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
            throw new IllegalStateException("epochSecond overflow for snowflake type "
                    + type + ": " + time);
        }
        return (time << epochSecondShift) | value;
    }

    /** 按 Snowflake 的两个低位字段拼接 ID。 */
    public final long pack(long epochSecond, long sequence) {
        requireRange("epochSecond", epochSecond, maxEpochSecond);
        requireRange("sequence", sequence, maxSequence);
        long id = pack((epochSecond << epochSecondShift) | sequence);
        if (id <= 0) {
            throw new IllegalStateException("generated snowflake id must be positive: " + id);
        }
        return id;
    }

    public final long decodeEpochSecond(long id) {
        requireLayout(id);
        return decodeIncrementId(id) >>> epochSecondShift;
    }

    public final long decodeSequence(long id) {
        requireLayout(id);
        return decodeIncrementId(id) & maxSequence;
    }

    /** 保留旧 API：Snowflake 的最高位固定为 0。 */
    public static void requireSignBit(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("snowflake sign bit must be 0: " + id);
        }
    }

    public abstract SnowflakeIdLayout find(long id);

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
}
