package org.evd.game.MatchService.entity.team;

import org.evd.game.MatchService.entity.team.MatchTeam;

/** 玩家到当前匹配队伍的反向索引。 */
public final class MatchPlayerData {
    private final int matchType;
    private final MatchTeam team;

    public MatchPlayerData(int matchType, MatchTeam team) {
        this.matchType = matchType;
        this.team = team;
    }

    public int getMatchType() { return matchType; }
    public MatchTeam getTeam() { return team; }
}
