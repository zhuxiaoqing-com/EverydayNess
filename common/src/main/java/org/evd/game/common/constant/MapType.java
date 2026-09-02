package org.evd.game.common.constant;

/** 地图配置对应的 Deal 类型。 */
public enum MapType {
    NORMAL(1);

    private final int type;

    MapType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }
}
