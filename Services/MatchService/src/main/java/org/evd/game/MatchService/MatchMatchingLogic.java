package org.evd.game.MatchService;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.MatchService.matchingPool.AbstractMatchingPool;
import org.evd.game.MatchService.matchingPool.impl.EatChickenMatchingPool;
import org.evd.game.MatchService.matchingPool.impl.PveMatchingPool;
import org.evd.game.MatchService.matchingPool.impl.Pvp5V5MatchingPool;
import org.evd.game.MatchService.matchingPool.impl.PvpMatchingPool;
import org.evd.game.MatchService.matchingPool.impl.RoomMatchingPool;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.constant.MatchType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 匹配执行逻辑：持有各匹配池，生成匹配结果并交给场景逻辑落地。 */
@Slf4j
@Actor
public final class MatchMatchingLogic {
    private final Map<Integer, AbstractMatchingPool> pools = new HashMap<>();
    private final AtomicBoolean matching = new AtomicBoolean();

    public MatchMatchingLogic() {
        pools.put(MatchType.PVP5V5_MATCH, new Pvp5V5MatchingPool());
        pools.put(MatchType.PVE_MATCH, new PveMatchingPool());
        pools.put(MatchType.EAT_CHICKEN, new EatChickenMatchingPool());
        pools.put(MatchType.PVP_MATCH, new PvpMatchingPool());
        pools.put(MatchType.ROOM_MATCH, new RoomMatchingPool());
    }

    public boolean addTeam(int matchType, MatchTeam team) {
        AbstractMatchingPool pool = pools.get(matchType);
        if (pool == null) return false;
        pool.addTeam(team);
        return true;
    }

    public void removeTeam(int matchType, MatchTeam team) {
        AbstractMatchingPool pool = pools.get(matchType);
        if (pool != null) pool.removeTeam(team);
    }

    public int getMatchPlayerNum(int matchType, int mapCfgId) {
        AbstractMatchingPool pool = pools.get(matchType);
        return pool == null ? 0 : pool.getMatchPlayerNum(mapCfgId);
    }

    public void match() {
        if (!matching.compareAndSet(false, true)) {
            log.warn("MatchService 上一次匹配尚未完成，跳过本次匹配轮次");
            return;
        }
        try {
            for (AbstractMatchingPool pool : pools.values()) {
                pool.matching();
            }
        } finally {
            matching.set(false);
        }
    }

}
