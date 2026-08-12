package org.evd.game.runtime.util.id;

import org.evd.game.runtime.util.id.multiNode.MultiNodeIdLayout0;
import org.evd.game.runtime.util.id.rollingServer.RollingServerIdLayout0;

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

    private static final SnowflakeIdLayout ROLLING_V0_DECODER = new RollingServerIdLayout0(0, 0);
    private static final SnowflakeIdLayout MULTI_NODE_V0_DECODER = new MultiNodeIdLayout0(0, 0, 0);

    private static volatile SnowflakeIdLayout layout = ROLLING_V0_DECODER;
    private static final GenerationState STATE = new GenerationState();

    /**
     * 保留旧类隐式公开的无参构造器；生成状态仍由静态 API 统一管理。
     */
    public SnowflakeIdGenerator() {
    }

    /**
     * 根据游戏部署类型选择 version 0 布局。
     */
    public static synchronized void init(SnowflakeIdType idType,
                                         int platformId, int playerServerId, int nodeId) {
        SnowflakeIdLayout newLayout = switch (idType) {
            case ROLLING_SERVER -> new RollingServerIdLayout0(platformId, playerServerId, nodeId);
            case MULTI_NODE -> new MultiNodeIdLayout0(platformId, playerServerId, nodeId);
        };
        layout = newLayout;
    }

    /**
     * 保留当前多 Node 初始化方式。
     */
    public static synchronized void init(int platformId, int playerServerId, int nodeId) {
        init(SnowflakeIdType.MULTI_NODE, platformId, playerServerId, nodeId);
    }

    /**
     * 创建玩家 ID。类级锁继续保证同一 JVM 内的 sequence 生成串行化。
     */
    public static synchronized long createPlayerId() {
        return layout.createId(currentEpochSecond(), STATE);
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
        int version = versionOf(id);
        SnowflakeIdType currentType = layout.type();
        return switch (currentType) {
            case ROLLING_SERVER -> switch (version) {
                case RollingServerIdLayout0.VERSION -> ROLLING_V0_DECODER;
                default -> throw unsupportedLayout(currentType, version);
            };
            case MULTI_NODE -> switch (version) {
                case MultiNodeIdLayout0.VERSION -> MULTI_NODE_V0_DECODER;
                default -> throw unsupportedLayout(currentType, version);
            };
        };
    }

    private static IllegalArgumentException unsupportedLayout(SnowflakeIdType idType, int version) {
        return new IllegalArgumentException("unsupported snowflake layout: type=" + idType
                + ", version=" + version);
    }

    private static long currentEpochSecond() {
        return Math.floorDiv(System.currentTimeMillis() - EPOCH_MILLIS, 1000);
    }

    private static void validatePositiveIdBits(long id) {
        SnowflakeIdLayout.requireSignBit(id);
    }

    /**
     * 进程级生成水位。切换 Layout 时继续复用，避免 sequence 状态被重置。
     */
    static final class GenerationState {
        /** 上一次观察到的物理时间，仅用于识别真实时钟回拨。 */
        long lastObservedEpochSecond = -1;
        /** 实际写入 ID 的逻辑时间，sequence 溢出时可以领先物理时间。 */
        long lastEpochSecond = -1;
        long sequence;
    }
}
