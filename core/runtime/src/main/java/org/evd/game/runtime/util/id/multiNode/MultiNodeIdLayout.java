package org.evd.game.runtime.util.id.multiNode;

import org.evd.game.runtime.util.id.SnowflakeIdLayout;
import org.evd.game.runtime.util.id.SnowflakeIdType;

import java.util.Map;

/** 多 Node Snowflake ID 布局族。具体版本由其子类定义。 */
public abstract class MultiNodeIdLayout extends SnowflakeIdLayout {

    public static final int VERSION = 1;
    public static final int VERSION_BITS = 1;

    private static final class VersionLayouts {
        private static final Map<Integer, LayoutFactory> FACTORY_MAP = Map.of(
                VERSION, MultiNodeIdLayout0::new);
        private static final Map<Integer, SnowflakeIdLayout> DEFAULT_LAYOUT_MAP = Map.of(
                VERSION, new MultiNodeIdLayout0(0, 0, 0));
    }

    protected MultiNodeIdLayout(int platformBits, int playerServerBits, int nodeBits,
                                int epochSecondBits, int sequenceBits,
                                int platformId, int playerServerId, int nodeId) {
        super(SnowflakeIdType.MULTI_NODE, VERSION, VERSION_BITS,
                platformBits, playerServerBits, nodeBits, epochSecondBits, sequenceBits,
                platformId, playerServerId, nodeId);
    }

    public static SnowflakeIdLayout create(int version, int platformId, int playerServerId, int nodeId) {
        LayoutFactory factory = VersionLayouts.FACTORY_MAP.get(version);
        if (factory == null) {
            throw new IllegalArgumentException("unsupported snowflake layout: type=MULTI_NODE, version=" + version);
        }
        return factory.create(platformId, playerServerId, nodeId);
    }

    public SnowflakeIdLayout find(long id) {
        for (SnowflakeIdLayout layout : VersionLayouts.DEFAULT_LAYOUT_MAP.values()) {
            if (layout.matches(id)) {
                return layout;
            }
        }
        return null;
    }
}
