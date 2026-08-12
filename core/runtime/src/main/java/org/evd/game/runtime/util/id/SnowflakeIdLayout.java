package org.evd.game.runtime.util.id;

/**
 * Snowflake ID 布局公共接口。
 *
 * <p>sign 和 version 位在所有版本中固定：bit 63 为 sign，bit 62~61 为 version，
 * 其余 bit 60~0 由具体布局定义。</p>
 */
public interface SnowflakeIdLayout {

    int SIGN_BITS = 1;
    int VERSION_BITS = 2;
    int VERSION_SHIFT = Long.SIZE - SIGN_BITS - VERSION_BITS;
    long VERSION_MASK = (1L << VERSION_BITS) - 1;

    int version();

    int sequenceBits();

    long maxEpochSecond();

    long pack(long epochSecond, long sequence);

    int decodePlatformId(long id);

    int decodePlayerServerId(long id);

    int decodeNodeId(long id);

    long decodeEpochSecond(long id);

    long decodeSequence(long id);

    default long sequenceMask() {
        return mask(sequenceBits());
    }

    static int extractVersion(long id) {
        return (int) ((id >>> VERSION_SHIFT) & VERSION_MASK);
    }

    static long versionBits(int version) {
        requireRange("version", version, VERSION_MASK);
        return (long) version << VERSION_SHIFT;
    }

    static long mask(int bits) {
        if (bits <= 0 || bits >= Long.SIZE) {
            throw new IllegalArgumentException("bits must be between 1 and 63: " + bits);
        }
        return (1L << bits) - 1;
    }

    static void requireRange(String field, long value, long maxValue) {
        if (value < 0 || value > maxValue) {
            throw new IllegalArgumentException(field + " out of range [0, " + maxValue + "]: " + value);
        }
    }

    static void requireSignBit(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("snowflake sign bit must be 0: " + id);
        }
    }

    static void requireTotalBits(String layoutName, int layoutBits) {
        int totalBits = SIGN_BITS + VERSION_BITS + layoutBits;
        if (totalBits != Long.SIZE) {
            throw new ExceptionInInitializerError(layoutName + " total bits must be "
                    + Long.SIZE + ", actual: " + totalBits);
        }
    }

}
