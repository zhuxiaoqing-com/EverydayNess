package org.evd.game.MatchService.entity.team;

import org.evd.game.common.serializeBean.MatchService.match.SMatchPlayer;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRequest;
import org.evd.game.common.serializeBean.MatchService.match.SMatchTeamRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** 匹配中的队伍；队伍是不可拆分的最小匹配单元。 */
public final class MatchTeam {
    private static final int ROBOT_ID_SEQUENCE_BITS = 10;
    private static final AtomicLong ROBOT_TEAM_ID = new AtomicLong(
            System.currentTimeMillis() << ROBOT_ID_SEQUENCE_BITS);
    private final long teamId;
    private final List<Integer> mapCfgIds;
    private final List<SMatchPlayer> members;
    private final boolean teamMatch;
    private final long matchTime;

    private MatchTeam(long teamId, List<Integer> mapCfgIds, List<SMatchPlayer> members,
                      boolean teamMatch) {
        this.teamId = teamId;
        this.mapCfgIds = new ArrayList<>(new LinkedHashSet<>(mapCfgIds));
        this.members = List.copyOf(members);
        this.teamMatch = teamMatch;
        this.matchTime = System.currentTimeMillis();
    }

    public static MatchTeam single(SMatchRequest request) {
        SMatchPlayer player = new SMatchPlayer(request.getPlayerId(), request.getPlayerService(), request.getDutyIds());
        return new MatchTeam(player.getPlayerId(), request.getMapCfgIds(), List.of(player), false);
    }

    public static MatchTeam team(SMatchTeamRequest request) {
        List<SMatchPlayer> members = new ArrayList<>();
        for (SMatchPlayer member : request.getMembers()) {
            members.add(new SMatchPlayer(member));
        }
        return new MatchTeam(request.getTeamId(), request.getMapCfgIds(), members, true);
    }

    public static MatchTeam robot(int duty) {
        long teamId = ROBOT_TEAM_ID.getAndIncrement();
        return new MatchTeam(teamId, List.of(),
                List.of(SMatchPlayer.robot(teamId, duty, "匹配机器人-" + teamId)), false);
    }

    public long getTeamId() { return teamId; }
    public List<Integer> getMapCfgIds() { return mapCfgIds; }
    public List<SMatchPlayer> getMembers() { return members; }
    public int getMemberSize() { return members.size(); }
    public long getMatchTime() { return matchTime; }
    public boolean isTeamMatch() { return teamMatch; }
    public boolean isRobot() { return members.stream().allMatch(SMatchPlayer::isRobot); }
}
