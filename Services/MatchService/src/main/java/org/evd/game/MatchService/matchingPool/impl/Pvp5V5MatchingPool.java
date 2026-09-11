package org.evd.game.MatchService.matchingPool.impl;

import org.evd.game.MatchService.matchType.AbsTeamMatching;
import org.evd.game.MatchService.matchType.Group5V5TeamMatching;

/** 5V5 使用普通双边匹配；当前项目的地图人数配置决定每边实际人数。 */
public final class Pvp5V5MatchingPool extends PvpMatchingPool {
    public Pvp5V5MatchingPool() { super(org.evd.game.common.constant.MatchType.PVP5V5_MATCH); }

    @Override
    protected AbsTeamMatching createMatcher(int sideLimit, int maxRobotNum, boolean needRobot,
                                            java.util.Map<Integer, Integer> dutyLimit) {
        return new Group5V5TeamMatching(sideLimit, maxRobotNum, needRobot, dutyLimit, true);
    }
}
