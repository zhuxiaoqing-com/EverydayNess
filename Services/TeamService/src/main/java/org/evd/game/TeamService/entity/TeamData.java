package org.evd.game.TeamService.entity;

import org.evd.game.common.serializeBean.MatchService.match.SMatchPlayer;
import org.evd.game.runtime.call.CallPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TeamService 内部维护的队伍状态；匹配提交时会转换成不可变快照。 */
public final class TeamData {
    private final long teamId;
    private long leaderId;
    private boolean matching;
    private final Map<Long, List<Integer>> memberDutyIds = new LinkedHashMap<>();
    private final Map<Long, CallPoint> memberServices = new LinkedHashMap<>();

    public TeamData(long teamId, long leaderId, CallPoint leaderService) {
        this.teamId = teamId;
        this.leaderId = leaderId;
        memberDutyIds.put(leaderId, new ArrayList<>());
        memberServices.put(leaderId, new CallPoint(leaderService));
    }

    public long getTeamId() {
        return teamId;
    }

    public long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(long leaderId) {
        this.leaderId = leaderId;
    }

    public boolean isMatching() {
        return matching;
    }

    public void setMatching(boolean matching) {
        this.matching = matching;
    }

    public Map<Long, List<Integer>> getMemberDutyIds() {
        return memberDutyIds;
    }

    public List<Long> getMemberIds() {
        return new ArrayList<>(memberDutyIds.keySet());
    }

    public void addMember(long playerId, CallPoint playerService) {
        memberDutyIds.put(playerId, new ArrayList<>());
        memberServices.put(playerId, new CallPoint(playerService));
    }

    public void removeMember(long playerId) {
        memberDutyIds.remove(playerId);
        memberServices.remove(playerId);
    }

    public List<SMatchPlayer> toMatchPlayers() {
        List<SMatchPlayer> result = new ArrayList<>(memberDutyIds.size());
        for (Map.Entry<Long, List<Integer>> entry : memberDutyIds.entrySet()) {
            result.add(new SMatchPlayer(entry.getKey(), memberServices.get(entry.getKey()), entry.getValue()));
        }
        return result;
    }
}
