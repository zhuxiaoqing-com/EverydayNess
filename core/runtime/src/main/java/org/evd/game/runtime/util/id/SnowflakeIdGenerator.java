package org.evd.game.runtime.util.id;

import org.evd.game.annotation.NodeType;
import org.evd.game.runtime.config.GlobalConfig;
import org.evd.game.runtime.config.NodeConfig;
import org.evd.game.runtime.config.NodeInfo;
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

    private static volatile SnowflakeIdLayout layout;
    private static final GenerationState STATE = new GenerationState();

    /**
     * 保留旧类隐式公开的无参构造器；生成状态仍由静态 API 统一管理。
     */
    public SnowflakeIdGenerator() {
    }

    /**
     * 根据游戏部署类型和全局配置选择 version 0 布局。
     *
     * <p>platformId、serverId 以及全部 GAME Node 的 nodeId 均从
     * {@link GlobalConfig} 读取。</p>
     */
    public static synchronized void init(SnowflakeIdType idType) {
        if (idType == null) {
            throw new IllegalArgumentException("snowflake idType is required");
        }

        NodeConfig config = GlobalConfig.requireNodeConfig();
        NodeInfo localNode = GlobalConfig.requireLocalNodeInfo();
        if (localNode.getNodeType() != NodeType.GAME) {
            throw new IllegalArgumentException("snowflake generator can only initialize on GAME node: nodeId="
                    + localNode.getNodeId() + ", nodeType=" + localNode.getNodeType());
        }

        SnowflakeIdLayout newLayout = createLayout(
                idType, config.getPlatformId(), config.getServerId(), 0);
        for (NodeInfo nodeInfo : config.getNodes()) {
            if (nodeInfo.getNodeType() == NodeType.GAME) {
                if (nodeInfo.getNodeId() > newLayout.maxNodeId()) {
                    throw new IllegalArgumentException("invalid GAME node for snowflake layout: nodeId="
                            + nodeInfo.getNodeId() + ", name=" + nodeInfo.getName()
                            + ", maxNodeId=" + newLayout.maxNodeId() + ", idType=" + idType);
                }
            }
        }

        layout = createLayout(idType, config.getPlatformId(), config.getServerId(), localNode.getNodeId());
    }

    /**
     * 创建玩家 ID。类级锁继续保证同一 JVM 内的 sequence 生成串行化。
     */
    public static synchronized long createPlayerId() {
        SnowflakeIdLayout currentLayout = layout;
        if (currentLayout == null) {
            throw new IllegalStateException("SnowflakeIdGenerator is not initialized");
        }
        return currentLayout.createId(currentEpochSecond(), STATE);
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
        SnowflakeIdLayout currentLayout = layout;
        if (currentLayout == null) {
            throw new IllegalStateException("SnowflakeIdGenerator is not initialized");
        }
        SnowflakeIdType currentType = currentLayout.type();
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

    private static SnowflakeIdLayout createLayout(SnowflakeIdType idType,
                                                   int platformId, int playerServerId, int nodeId) {
        return switch (idType) {
            case ROLLING_SERVER -> new RollingServerIdLayout0(platformId, playerServerId, nodeId);
            case MULTI_NODE -> new MultiNodeIdLayout0(platformId, playerServerId, nodeId);
        };
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
