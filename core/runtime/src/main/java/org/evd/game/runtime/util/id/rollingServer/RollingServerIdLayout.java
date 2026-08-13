package org.evd.game.runtime.util.id.rollingServer;

import org.evd.game.runtime.util.id.SnowflakeIdLayout;
import org.evd.game.runtime.util.id.SnowflakeIdType;

import java.util.Map;

/** 滚服 Snowflake ID 布局族。具体版本由其子类定义。 */
public abstract class RollingServerIdLayout extends SnowflakeIdLayout {

    public static final int VERSION = 0;
    public static final int VERSION_BITS = 1;

    private static final class VersionLayouts {
        private static final Map<Integer, LayoutFactory> FACTORY_MAP = Map.of(
                VERSION, RollingServerIdLayout0::new);
        private static final Map<Integer, SnowflakeIdLayout> DEFAULT_LAYOUT_MAP = Map.of(
                VERSION, new RollingServerIdLayout0(0, 0, 0));
    }

    protected RollingServerIdLayout(int platformBits, int playerServerBits, int nodeBits,
                                    int epochSecondBits, int sequenceBits,
                                    int platformId, int playerServerId, int nodeId) {
        super(SnowflakeIdType.ROLLING_SERVER, VERSION, VERSION_BITS,
                platformBits, playerServerBits, nodeBits, epochSecondBits, sequenceBits,
                platformId, playerServerId, nodeId);
    }

    public static SnowflakeIdLayout create(int version, int platformId, int playerServerId, int nodeId) {
        LayoutFactory factory = VersionLayouts.FACTORY_MAP.get(version);
        if (factory == null) {
            throw new IllegalArgumentException("unsupported snowflake layout: type=ROLLING_SERVER, version=" + version);
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
