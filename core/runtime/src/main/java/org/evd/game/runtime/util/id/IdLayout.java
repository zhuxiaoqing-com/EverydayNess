package org.evd.game.runtime.util.id;

/**
 * ID 布局的公共位编码实现。
 *
 * <p>最高位固定为布局标识，随后是 1 bit 版本号；其余字段由具体布局声明。
 * Snowflake 使用低位保存时间和序列，MySQL 段号布局则使用低位保存数据库申请的
 * 自增段号。</p>
 */
public abstract class IdLayout {

    private static final int FIXED_SHIFT = Long.SIZE - 1;
    private static final int VERSION_SHIFT = Long.SIZE - 2;
    private static final int FIXED_BITS = 1;
    private static final int VERSION_BITS = 1;

    private final int fixed;
    private final int version;
    private final int platformShift;
    private final int playerServerShift;
    private final int nodeShift;
    private final int incrementBits;
    private final long platformMask;
    private final long playerServerMask;
    private final long nodeMask;
    private final long incrementMask;
    private final int platformId;
    private final int playerServerId;
    private final int nodeId;

    protected IdLayout(int fixed, int version, int platformBits, int playerServerBits,
                       int nodeBits, int incrementBits, int platformId,
                       int playerServerId, int nodeId) {
        requireRange("fixed", fixed, 1);
        requireRange("version", version, mask(VERSION_BITS));
        requireBitCount("platformBits", platformBits);
        requireBitCount("playerServerBits", playerServerBits);
        requireBitCount("nodeBits", nodeBits);
        requireBitCount("incrementBits", incrementBits);
        requireTotalBits(platformBits, playerServerBits, nodeBits, incrementBits);

        this.fixed = fixed;
        this.version = version;
        this.incrementBits = incrementBits;
        this.nodeShift = incrementBits;
        this.playerServerShift = nodeShift + nodeBits;
        this.platformShift = playerServerShift + playerServerBits;
        this.platformMask = mask(platformBits);
        this.playerServerMask = mask(playerServerBits);
        this.nodeMask = mask(nodeBits);
        this.incrementMask = mask(incrementBits);
        requireRange("platformId", platformId, platformMask);
        requireRange("playerServerId", playerServerId, playerServerMask);
        requireRange("nodeId", nodeId, nodeMask);
        this.platformId = platformId;
        this.playerServerId = playerServerId;
        this.nodeId = nodeId;
    }

    /** 创建当前布局的下一个 ID。具体布局只负责提供低位自增值。 */
    public final long createId(IDEnum idEnum) {
        if (idEnum == null) {
            throw new IllegalArgumentException("idEnum is required");
        }
        return pack(nextIncrementId(idEnum));
    }

    protected abstract long nextIncrementId(IDEnum idEnum);

    /** 从当前布局族中查找能够解析指定 ID 的版本布局。 */
    public abstract IdLayout find(long id);

    public final int fixedOf(long id) {
        return (int) (id >>> FIXED_SHIFT);
    }

    public final int versionOf(long id) {
        return (int) ((id >>> VERSION_SHIFT) & mask(VERSION_BITS));
    }

    public final int version() {
        return version;
    }

    public final long pack(long incrementId) {
        requireRange("incrementId", incrementId, incrementMask);
        return ((long) fixed << FIXED_SHIFT)
                | ((long) version << VERSION_SHIFT)
                | ((long) platformId << platformShift)
                | ((long) playerServerId << playerServerShift)
                | ((long) nodeId << nodeShift)
                | incrementId;
    }

    public final int decodePlatformId(long id) {
        requireLayout(id);
        return (int) ((id >>> platformShift) & platformMask);
    }

    public final int decodePlayerServerId(long id) {
        requireLayout(id);
        return (int) ((id >>> playerServerShift) & playerServerMask);
    }

    public final int decodeNodeId(long id) {
        requireLayout(id);
        return (int) ((id >>> nodeShift) & nodeMask);
    }

    public final long decodeIncrementId(long id) {
        requireLayout(id);
        return id & incrementMask;
    }

    public final boolean matches(long id) {
        return fixedOf(id) == fixed && versionOf(id) == version;
    }

    protected final void requireLayout(long id) {
        if (!matches(id)) {
            throw new IllegalArgumentException("unexpected id layout: fixed=" + fixedOf(id)
                    + ", version=" + versionOf(id) + ", expected fixed=" + fixed
                    + ", version=" + version);
        }
    }

    protected final long maxIncrementId() {
        return incrementMask;
    }

    public final long maxNodeId() {
        return nodeMask;
    }

    private static long mask(int bits) {
        if (bits <= 0 || bits >= Long.SIZE) {
            throw new IllegalArgumentException("bits must be between 1 and 63: " + bits);
        }
        return (1L << bits) - 1;
    }

    private static void requireBitCount(String field, int bits) {
        if (bits <= 0 || bits >= Long.SIZE - FIXED_BITS - VERSION_BITS) {
            throw new IllegalArgumentException(field + " must be between 1 and 61: " + bits);
        }
    }

    private static void requireTotalBits(int platformBits, int playerServerBits,
                                         int nodeBits, int incrementBits) {
        int totalBits = FIXED_BITS + VERSION_BITS + platformBits + playerServerBits
                + nodeBits + incrementBits;
        if (totalBits > Long.SIZE) {
            throw new IllegalArgumentException("id layout total bits must be at most "
                    + Long.SIZE + ", actual: " + totalBits);
        }
    }

    private static void requireRange(String field, long value, long maxValue) {
        if (value < 0 || value > maxValue) {
            throw new IllegalArgumentException(field + " out of range [0, " + maxValue + "]: " + value);
        }
    }
}
