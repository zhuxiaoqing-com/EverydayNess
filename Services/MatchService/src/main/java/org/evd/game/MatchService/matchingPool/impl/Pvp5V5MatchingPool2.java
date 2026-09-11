package org.evd.game.MatchService.matchingPool.impl;

import org.evd.game.MatchService.matchType.AbsTeamMatching;
import org.evd.game.MatchService.matchType.Group5V5TeamMatching2;

/** PVP5V5 的第二套队伍组合匹配池。 */
public final class Pvp5V5MatchingPool2 extends PvpMatchingPool {
    public Pvp5V5MatchingPool2() { super(org.evd.game.common.constant.MatchType.PVP5V5_MATCH); }

    @Override
    protected AbsTeamMatching createMatcher(int sideLimit, int maxRobotNum, boolean needRobot,
                                            java.util.Map<Integer, Integer> dutyLimit) {
        return new Group5V5TeamMatching2(sideLimit, maxRobotNum, needRobot, dutyLimit, true);
    }
}
