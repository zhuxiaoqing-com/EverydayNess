package org.evd.game.MatchService.entity.pool;

import org.evd.game.common.serializeBean.MatchService.match.SMatchPlayer;

import java.util.ArrayList;
import java.util.List;

/** 一个副本职责候选队列；没有职责配置时不会参与筛选。 */
public final class MatchDutyObj {
    private final int duty;
    private final List<SMatchPlayer> players = new ArrayList<>();

    public MatchDutyObj(int duty) { this.duty = duty; }

    public void add(SMatchPlayer player) { players.add(player); }
    public void remove(SMatchPlayer player) { players.removeIf(item -> item.getPlayerId() == player.getPlayerId()); }
    public int getDuty() { return duty; }
    public int getRoleNum() { return players.size(); }
    public List<SMatchPlayer> getPlayers() { return players; }
}
