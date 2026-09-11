package org.evd.game.MatchService.config;

import org.evd.game.common.constant.MatchType;

import java.util.Map;

/** 匹配机器人统一规则；当前先固定在代码中，后续有配置系统时只替换这里。 */
public final class MatchRobotConfig {
    public static final long WAIT_MILLIS = 30_000L;
    public static final int PVE_MAX_ROBOT_NUM = 5;
    public static final int PVP_MAX_ROBOT_NUM = 10;
    private static final Map<Integer, Map<Integer, Integer>> DUTY_LIMITS = Map.of();

    private MatchRobotConfig() {
    }

    public static boolean shouldFill(long earliestMatchTime) {
        return earliestMatchTime > 0L
                && System.currentTimeMillis() - earliestMatchTime >= WAIT_MILLIS;
    }

    public static int maxRobotNum(int matchType, int playerLimit, int realPlayerNum) {
        int configuredMax = matchType == MatchType.PVE_MATCH
                ? PVE_MAX_ROBOT_NUM : PVP_MAX_ROBOT_NUM;
        return Math.min(configuredMax, Math.max(0, playerLimit - realPlayerNum));
    }

    public static Map<Integer, Integer> getDutyLimits(int mapCfgId) {
        return DUTY_LIMITS.getOrDefault(mapCfgId, Map.of());
    }
}
