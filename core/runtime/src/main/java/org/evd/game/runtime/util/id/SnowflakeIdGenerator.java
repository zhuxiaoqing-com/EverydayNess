package org.evd.game.runtime.util.id;

import org.evd.game.annotation.node.NodeType;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.ymlconfig.NodeYml;
import org.evd.game.runtime.ymlconfig.NodeInfo;
import org.evd.game.runtime.util.id.multiNode.MultiNodeIdLayout;
import org.evd.game.runtime.util.id.idSegment.IdSegmentLayout;
import org.evd.game.runtime.util.id.rollingServer.RollingServerIdLayout;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Snowflake ID 兼容入口。
 *
 * <p>布局版本、配置校验、生成状态和解析均由 {@link SnowflakeIdLayout} 统一维护。
 * 新代码直接使用 {@code SnowflakeIdLayout}；本类保留以兼容既有调用。</p>
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

    private static volatile IdLayout currentLayout;

    private static final Map<SnowflakeIdType, LayoutFactory> LAYOUT_FACTORY_MAP = Map.of(
            SnowflakeIdType.ROLLING_SERVER, RollingServerIdLayout::create,
            SnowflakeIdType.MULTI_NODE, MultiNodeIdLayout::create,
            SnowflakeIdType.MYSQL, IdSegmentLayout::create);

    @FunctionalInterface
    private interface LayoutFactory {
        IdLayout create(int version, int platformId, int playerServerId, int nodeId);
    }

    /**
     * 保留旧类隐式公开的无参构造器；生成状态仍由静态 API 统一管理。
     */
    public SnowflakeIdGenerator() {
    }

    /**
     * 根据游戏部署类型、布局版本和全局配置初始化 Snowflake 布局。
     *
     * <p>platformId、serverId 以及全部 GAME Node 的 nodeId 校验由布局实现完成。</p>
     */
    public static void init(SnowflakeIdType idType, int version) {
        if (idType == null) {
            throw new IllegalArgumentException("snowflake idType is required");
        }
        LayoutFactory layoutFactory = LAYOUT_FACTORY_MAP.get(idType);
        if (layoutFactory == null) {
            throw new IllegalArgumentException("unsupported snowflake idType: " + idType);
        }
        synchronized (SnowflakeIdLayout.class) {
            NodeYml config = GlobalYml.requireNodeConfig();
            NodeInfo localNode = GlobalYml.requireLocalNodeInfo();
            if (localNode.getNodeType() != NodeType.GAME) {
                throw new IllegalArgumentException("snowflake generator can only initialize on GAME node: nodeId="
                        + localNode.getNodeId() + ", nodeType=" + localNode.getNodeType());
            }

            IdLayout layoutForValidation = layoutFactory.create(
                    version, config.getPlatformId(), config.getServerId(), 0);
            try {
                for (NodeInfo nodeInfo : config.getNodes()) {
                    if (nodeInfo.getNodeType() == NodeType.GAME && nodeInfo.getNodeId() > layoutForValidation.maxNodeId()) {
                        throw new IllegalArgumentException("invalid GAME node for snowflake layout: nodeId="
                                + nodeInfo.getNodeId() + ", name=" + nodeInfo.getName()
                                + ", maxNodeId=" + layoutForValidation.maxNodeId()
                                + ", idType=" + idType);
                    }
                }
            } finally {
                if (layoutForValidation instanceof IdSegmentLayout idSegmentLayout) {
                    idSegmentLayout.close();
                }
            }

            currentLayout = layoutFactory.create(
                    version, config.getPlatformId(), config.getServerId(), localNode.getNodeId());
        }
    }

    /**
     * 创建玩家 ID。
     */
    public static long createPlayerId() {
        IdLayout layout = currentLayout;
        if (layout == null) {
            throw new IllegalStateException("SnowflakeIdGenerator is not initialized");
        }
        return layout.createId(IDEnum.PLAYER);
    }

    /**
     * 从全局固定位置读取 version，不依赖任何具体布局。
     */
    public static int versionOf(long id) {
        return requireCurrentLayout().versionOf(id);
    }

    public static int decodePlatformId(long id) {
        return requireDecoderLayout(id).decodePlatformId(id);
    }

    public static int decodePlayerServerId(long id) {
        return requireDecoderLayout(id).decodePlayerServerId(id);
    }

    public static int decodeNodeId(long id) {
        return requireDecoderLayout(id).decodeNodeId(id);
    }

    public static long decodeEpochSecond(long id) {
        IdLayout layout = requireDecoderLayout(id);
        if (!(layout instanceof SnowflakeIdLayout snowflakeLayout)) {
            throw new IllegalArgumentException("decodeEpochSecond is only supported for snowflake layout: id=" + id);
        }
        return snowflakeLayout.decodeEpochSecond(id);
    }

    public static long decodeSequence(long id) {
        IdLayout layout = requireDecoderLayout(id);
        if (!(layout instanceof SnowflakeIdLayout snowflakeLayout)) {
            throw new IllegalArgumentException("decodeSequence is only supported for snowflake layout: id=" + id);
        }
        return snowflakeLayout.decodeSequence(id);
    }

    public static long decodeIncrementId(long id) {
        return requireDecoderLayout(id).decodeIncrementId(id);
    }

    private static IdLayout requireDecoderLayout(long id) {
        IdLayout layout = requireCurrentLayout().find(id);
        if (layout == null) {
            throw new IllegalArgumentException("unsupported snowflake layout: id=" + id);
        }
        return layout;
    }

    private static IdLayout requireCurrentLayout() {
        IdLayout layout = currentLayout;
        if (layout == null) {
            throw new IllegalStateException("SnowflakeIdGenerator is not initialized");
        }
        return layout;
    }

}
