package org.evd.game.MatchService.matchingPool;

/** 兼容参考项目的旧单人池类型；当前单人入口统一包装成一人队伍。 */
@Deprecated
public abstract class SingleMatchingPool extends TeamMatchingPool {
    protected SingleMatchingPool(int matchType) { super(matchType); }
}
