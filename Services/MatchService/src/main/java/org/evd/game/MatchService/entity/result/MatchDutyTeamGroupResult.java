package org.evd.game.MatchService.entity.result;

import org.evd.game.MatchService.entity.team.MatchTeam;

import java.util.HashMap;
import java.util.Map;

/**
 * 带职责约束的一方匹配结果。
 *
 * <p>真人玩家只能从自己声明的职责列表中参与匹配；机器人只负责填充
 * 真人匹配完成后仍然缺少的职责。</p>
 */
public final class MatchDutyTeamGroupResult extends MatchTeamGroupResult {
    private final Map<Integer, Integer> currNeedDuty;
    private final boolean needRobot;

    public MatchDutyTeamGroupResult(Map<Integer, Integer> dutyLimit) {
        this(false, dutyLimit);
    }

    public MatchDutyTeamGroupResult(boolean needRobot, Map<Integer, Integer> dutyLimit) {
        this.currNeedDuty = new HashMap<>(dutyLimit);
        this.needRobot = needRobot;
    }

    @Override
    public boolean checkValid(MatchTeam team) {
        return checkValid(team, false);
    }

    public boolean checkValid(MatchTeam team, boolean add) {
        if (team == null || team.getMemberSize() <= 0) return false;
        Map<Integer, Integer> remaining = new HashMap<>(currNeedDuty);
        for (var player : team.getMembers()) {
            boolean success = false;
            for (Integer duty : player.getDutyIds()) {
                Integer count = remaining.get(duty);
                // 该地图不需要这个职责，不能用它参与当前匹配。
                if (count == null) {
                    continue;
                }
                // 不允许机器人补位时，职责名额用完后不能再接纳真人。
                if (!needRobot && count <= 0) {
                    continue;
                }
                if (add) {
                    // 记录真人最终选择的职责，不能改成玩家没有声明过的职责。
                    player.setSelectedDuty(duty);
                }
                int consumeDuty = duty;
                if (count <= 0) {
                    // 允许机器人补位时，真人可以继续使用自己声明的职责；
                    // 但本次匹配实际消耗一个仍有需求的职责名额。
                    consumeDuty = remaining.entrySet().stream()
                            .filter(entry -> entry.getValue() > 0)
                            .findAny()
                            .map(Map.Entry::getKey)
                            .orElse(0);
                }
                remaining.merge(consumeDuty, -1, Integer::sum);
                success = true;
                break;
            }
            if (!success) {
                // 真人没有任何可参与当前副本的职责，整支队伍不能加入该结果。
                return false;
            }
        }
        if (add) {
            currNeedDuty.clear();
            currNeedDuty.putAll(remaining);
        }
        return true;
    }

    @Override
    public void addTeams(MatchTeam team) {
        if (checkValid(team, true)) super.addTeams(team);
    }

    public void fillRobotTeam(int maxRoleNum) {
        for (Map.Entry<Integer, Integer> entry : currNeedDuty.entrySet()) {
            // 机器人只填充仍有正数需求的职责，不使用无职责的兜底机器人。
            for (int i = Math.max(0, entry.getValue()); i > 0 && roleNum < maxRoleNum; i--) {
                addTeams(MatchTeam.robot(entry.getKey()));
            }
        }
    }
}
