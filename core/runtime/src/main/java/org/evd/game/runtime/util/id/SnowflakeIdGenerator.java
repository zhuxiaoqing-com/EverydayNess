package org.evd.game.runtime.util.id;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Snowflake ID 统一生成与解析入口。
 *
 * <p>所有版本共用同一个纪元、时钟检查和 sequence 生成逻辑，具体字段布局由
 * {@link SnowflakeIdLayout} 负责。</p>
 *
 * @author zhuxiaoqing
 * @Description: SnowflakeIdGenerator 雪花算法
 * @Date 2026/1/15 14:42
 */
public final class SnowflakeIdGenerator {

    /** 所有 Snowflake 版本统一使用的起始时间：2024-01-01 00:00:00 UTC+8。 */
    public static final long EPOCH_MILLIS = LocalDate.of(2024, 1, 1)
            .atStartOfDay()
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli();

    private static final SnowflakeIdLayout V0_DECODER = new RollingServerIdLayout(0, 0);
    private static final SnowflakeIdLayout V1_DECODER = new MultiNodeIdLayout(0, 0, 0);

    private static SnowflakeIdLayout layout = V0_DECODER;
    private static long lastEpochSecond = -1;
    private static long sequence;

    /**
     * 保留旧类隐式公开的无参构造器；生成状态仍由静态 API 统一管理。
     */
    public SnowflakeIdGenerator() {
    }

    /**
     * 使用滚服布局初始化，并指定滚服节点编号。
     */
    public static synchronized void init(int platformId, int playerServerId, int nodeId) {
        layout = new MultiNodeIdLayout(platformId, playerServerId, nodeId);
        // 检查是否合法
    }

    /**
     * 创建玩家 ID。类级锁继续保证同一 JVM 内的 sequence 生成串行化。
     */
    public static synchronized long createPlayerId() {
        long nowEpochSecond = currentEpochSecond();
        if (nowEpochSecond < 0) {
            throw new IllegalStateException("current time is before snowflake epoch: " + nowEpochSecond);
        }
        if (lastEpochSecond >= 0 && nowEpochSecond < lastEpochSecond) {
            throw new IllegalStateException("clock moved backwards: current=" + nowEpochSecond
                    + ", last=" + lastEpochSecond);
        }

        if (nowEpochSecond > lastEpochSecond) {
            lastEpochSecond = nowEpochSecond;
            sequence = 0;
        } else {
            sequence++;
            if (sequence > layout.sequenceMask()) {
                lastEpochSecond = waitUntilNextSecond(lastEpochSecond);
                sequence = 0;
            }
        }

        if (lastEpochSecond > layout.maxEpochSecond()) {
            throw new IllegalStateException("epochSecond overflow for snowflake version "
                    + layout.version() + ": " + lastEpochSecond);
        }

        long id = layout.pack(lastEpochSecond, sequence);
        if (id <= 0) {
            throw new IllegalStateException("generated snowflake id must be positive: " + id);
        }
        return id;
    }

    /**
     * 从全局固定位置读取 version，不依赖任何具体布局。
     */
    public static int versionOf(long id) {
        validatePositiveIdBits(id);
        return SnowflakeIdLayout.extractVersion(id);
    }

    public static int decodePlatformId(long id) {
        return layoutOf(id).decodePlatformId(id);
    }

    public static int decodePlayerServerId(long id) {
        return layoutOf(id).decodePlayerServerId(id);
    }

    public static int decodeNodeId(long id) {
        return layoutOf(id).decodeNodeId(id);
    }

    public static long decodeEpochSecond(long id) {
        return layoutOf(id).decodeEpochSecond(id);
    }

    public static long decodeSequence(long id) {
        return layoutOf(id).decodeSequence(id);
    }

    private static SnowflakeIdLayout layoutOf(long id) {
        return switch (versionOf(id)) {
            case RollingServerIdLayout.VERSION -> V0_DECODER;
            case MultiNodeIdLayout.VERSION -> V1_DECODER;
            default -> throw new IllegalArgumentException("unsupported snowflake version: "
                    + SnowflakeIdLayout.extractVersion(id));
        };
    }

    private static long currentEpochSecond() {
        return Math.floorDiv(System.currentTimeMillis() - EPOCH_MILLIS, 1000);
    }

    private static long waitUntilNextSecond(long previousEpochSecond) {
        long nowEpochSecond = currentEpochSecond();
        while (nowEpochSecond <= previousEpochSecond) {
            if (nowEpochSecond < previousEpochSecond) {
                throw new IllegalStateException("clock moved backwards while waiting for next second: current="
                        + nowEpochSecond + ", last=" + previousEpochSecond);
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for next second", e);
            }
            nowEpochSecond = currentEpochSecond();
        }
        return nowEpochSecond;
    }

    private static void validatePositiveIdBits(long id) {
        SnowflakeIdLayout.requireSignBit(id);
    }
}
