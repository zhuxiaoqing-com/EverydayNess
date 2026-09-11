package org.evd.game.common.constant;

/** 匹配类型；每一种类型对应一个固定的匹配池。 */
public final class MatchType {
    public static final int ENTER_MAP_MATCH = 0;
    public static final int PVP5V5_MATCH = 1;
    public static final int PVE_MATCH = 2;
    public static final int EAT_CHICKEN = 3;
    public static final int PVP_MATCH = 4;
    public static final int ROOM_MATCH = 5;

    private MatchType() {
    }

    public static boolean isValid(int matchType) {
        return matchType >= PVP5V5_MATCH && matchType <= ROOM_MATCH;
    }
}
