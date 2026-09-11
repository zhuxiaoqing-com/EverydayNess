package org.evd.game.MatchService;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.MatchService.entity.team.MatchPlayerData;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.constant.MatchType;
import org.evd.game.common.constant.TeamConst;
import org.evd.game.common.proxy.PlayerService.PlayerMapRpcProxy;
import org.evd.game.common.proxy.TeamService.TeamMatchRpcProxy;
import org.evd.game.common.serializeBean.MatchService.match.SMatchPlayer;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRequest;
import org.evd.game.common.serializeBean.MatchService.match.SMatchTeamRequest;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.Service;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.call.CallPoint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 匹配队列逻辑：负责玩家、队伍加入/取消和匹配状态索引。 */
@Slf4j
@Actor
public final class MatchLogic {
    private final Map<Long, MatchPlayerData> player2Data = new HashMap<>();

    public boolean match(SMatchRequest request) {
        if (request == null) {
            log.warn("MatchService 单人匹配失败：请求为空");
            return false;
        }
        return addTeam(request.getMatchType(), MatchTeam.single(request));
    }

    public boolean teamMatch(SMatchTeamRequest request) {
        if (request == null) {
            log.warn("MatchService 组队匹配失败：请求为空");
            return false;
        }
        if (request.getTeamId() <= 0L) {
            log.warn("MatchService 组队匹配失败：队伍 ID 无效，teamId={}", request.getTeamId());
            return false;
        }
        if (request.getMembers() == null || request.getMembers().isEmpty()) {
            log.warn("MatchService 组队匹配失败：队伍成员为空，teamId={}", request.getTeamId());
            return false;
        }
        Set<Long> memberIds = new HashSet<>();
        for (SMatchPlayer member : request.getMembers()) {
            if (member == null) {
                log.warn("MatchService 组队匹配失败：成员为空，teamId={}", request.getTeamId());
                return false;
            }
            if (member.getPlayerId() <= 0L || member.getPlayerService() == null) {
                log.warn("MatchService 组队匹配失败：成员信息无效，teamId={}, playerId={}",
                        request.getTeamId(), member.getPlayerId());
                return false;
            }
            if (!memberIds.add(member.getPlayerId())) {
                log.warn("MatchService 组队匹配失败：成员重复，teamId={}, playerId={}",
                        request.getTeamId(), member.getPlayerId());
                return false;
            }
        }
        return addTeam(request.getMatchType(), MatchTeam.team(request));
    }

    public boolean cancel(long playerId) {
        MatchPlayerData data = player2Data.get(playerId);
        if (data == null) {
            log.warn("MatchService 取消匹配失败：玩家不在匹配队列，playerId={}", playerId);
            return false;
        }
        removeTeam(data.getMatchType(), data.getTeam());
        log.info("MatchService 玩家取消匹配: playerId={}, teamId={}", playerId, data.getTeam().getTeamId());
        return true;
    }

    public boolean cancelTeam(long teamId) {
        MatchPlayerData data = player2Data.values().stream()
                .filter(item -> item.getTeam().getTeamId() == teamId)
                .findFirst().orElse(null);
        if (data == null) {
            log.warn("MatchService 取消组队匹配失败：队伍不在匹配队列，teamId={}", teamId);
            return false;
        }
        removeTeam(data.getMatchType(), data.getTeam());
        return true;
    }

    public int getMatchPlayerNum(int matchType, int mapCfgId) {
        int playerNum = matchingLogic().getMatchPlayerNum(matchType, mapCfgId);
        if (playerNum == 0 && !MatchType.isValid(matchType)) {
            log.warn("MatchService 查询匹配人数失败：匹配类型不存在，matchType={}, mapCfgId={}",
                    matchType, mapCfgId);
        }
        return playerNum;
    }

    /** 调用方已在 PlayerService 占用玩家匹配状态后，才登记到 MatchService 队列。 */
    private boolean addTeam(int matchType, MatchTeam team) {
        if (!validTeam(matchType, team)) {
            log.warn("MatchService 加入匹配队列失败：队伍参数校验失败，matchType={}, teamId={}",
                    matchType, team == null ? null : team.getTeamId());
            return false;
        }
        if (containsTeam(team.getTeamId())) {
            log.warn("MatchService 加入匹配队列失败：队伍已在匹配队列，teamId={}", team.getTeamId());
            return false;
        }
        for (SMatchPlayer member : team.getMembers()) {
            if (player2Data.containsKey(member.getPlayerId())) {
                log.warn("MatchService 加入匹配队列失败：玩家已在匹配队列，teamId={}, playerId={}",
                        team.getTeamId(), member.getPlayerId());
                return false;
            }
        }

        if (!matchingLogic().addTeam(matchType, team)) {
            log.error("MatchService 加入匹配队列失败：匹配池不存在，matchType={}, teamId={}",
                    matchType, team.getTeamId());
            return false;
        }

        for (SMatchPlayer member : team.getMembers()) {
            player2Data.put(member.getPlayerId(), new MatchPlayerData(matchType, team));
        }
        log.info("MatchService 加入匹配队列: teamId={}, matchType={}, mapCfgIds={}",
                team.getTeamId(), matchType, team.getMapCfgIds());
        return true;
    }

    private void removeTeam(Integer matchType, MatchTeam team) {
        if (matchType == null) return;
        matchingLogic().removeTeam(matchType, team);
        cancelPlayerState(team);
        notifyTeamMatchResult(List.of(team), matchType, false, null);
    }

    public void removeMatchedTeams(List<MatchTeam> teams) {
        for (MatchTeam team : teams) {
            removeTeamData(team);
        }
    }

    public void notifyTeamMatchResult(List<MatchTeam> teams, int matchType,
                                      boolean success, SMapInfo mapInfo) {
        CallPoint teamService = TeamConst.getTeamCallPoint();
        for (MatchTeam team : teams) {
            if (!team.isTeamMatch() || team.isRobot()) {
                continue;
            }
            RpcResult<Void> result = TeamMatchRpcProxy.sendMatchResult(
                    teamService, team.getTeamId(), matchType, success, mapInfo);
            if (!result.isSuccess()) {
                log.error("MatchService 通知 TeamService 组队匹配结果失败: teamId={}, matchType={}, success={}, errorCode={}, message={}",
                        team.getTeamId(), matchType, success,
                        result.getErrorCode(), result.getErrorMessage());
            }
        }
    }

    public void clearMatchState(List<MatchTeam> teams) {
        for (MatchTeam team : teams) {
            for (SMatchPlayer member : team.getMembers()) {
                if (member.isRobot()) {
                    continue;
                }
                RpcResult<Void> result = PlayerMapRpcProxy.sendClearMatchState(
                        member.getPlayerService(), member.getPlayerId());
                if (!result.isSuccess()) {
                    log.warn("MatchService 清理玩家匹配状态失败: playerId={}, teamId={}, errorCode={}, message={}",
                            member.getPlayerId(), team.getTeamId(),
                            result.getErrorCode(), result.getErrorMessage());
                }
            }
        }
    }

    private void removeTeamData(MatchTeam team) {
        for (SMatchPlayer member : team.getMembers()) {
            MatchPlayerData current = player2Data.get(member.getPlayerId());
            if (current != null && current.getTeam() == team) {
                player2Data.remove(member.getPlayerId());
            }
        }
    }

    private void cancelPlayerState(MatchTeam team) {
        for (SMatchPlayer member : team.getMembers()) {
            RpcResult<Boolean> result = PlayerMapRpcProxy.callCancelMatch(
                    member.getPlayerService(), member.getPlayerId());
            if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
                log.warn("MatchService 清理玩家匹配状态失败: playerId={}, teamId={}, errorCode={}, message={}",
                        member.getPlayerId(), team.getTeamId(), result.getErrorCode(), result.getErrorMessage());
            }
        }
    }

    private boolean containsTeam(long teamId) {
        return player2Data.values().stream()
                .anyMatch(data -> data.getTeam().getTeamId() == teamId);
    }

    private MatchMatchingLogic matchingLogic() {
        return Service.getCurrent(MatchService.class).getActor(MatchMatchingLogic.class);
    }

    private boolean validTeam(int matchType, MatchTeam team) {
        if (team == null) {
            log.warn("MatchService 队伍校验失败：队伍为空，matchType={}", matchType);
            return false;
        }
        if (team.getTeamId() <= 0L) {
            log.warn("MatchService 队伍校验失败：队伍 ID 无效，matchType={}, teamId={}",
                    matchType, team.getTeamId());
            return false;
        }
        if (!MatchType.isValid(matchType)) {
            log.warn("MatchService 队伍校验失败：匹配类型无效，matchType={}, teamId={}",
                    matchType, team.getTeamId());
            return false;
        }
        if (team.getMembers().isEmpty() || team.getMembers().size() > 5) {
            log.warn("MatchService 队伍校验失败：成员数量无效，teamId={}, memberCount={}",
                    team.getTeamId(), team.getMembers().size());
            return false;
        }
        if (team.getMapCfgIds().isEmpty()) {
            log.warn("MatchService 队伍校验失败：地图候选为空，teamId={}", team.getTeamId());
            return false;
        }
        Set<Long> memberIds = new HashSet<>();
        for (SMatchPlayer member : team.getMembers()) {
            if (member == null) {
                log.warn("MatchService 队伍校验失败：成员为空，teamId={}", team.getTeamId());
                return false;
            }
            if (member.getPlayerId() <= 0L || member.getPlayerService() == null) {
                log.warn("MatchService 队伍校验失败：成员信息无效，teamId={}, playerId={}",
                        team.getTeamId(), member.getPlayerId());
                return false;
            }
            if (!memberIds.add(member.getPlayerId())) {
                log.warn("MatchService 队伍校验失败：成员重复，teamId={}, playerId={}",
                        team.getTeamId(), member.getPlayerId());
                return false;
            }
            for (Integer dutyId : member.getDutyIds()) {
                if (dutyId == null || dutyId <= 0) {
                    log.warn("MatchService 队伍校验失败：职责 ID 无效，teamId={}, playerId={}, dutyId={}",
                            team.getTeamId(), member.getPlayerId(), dutyId);
                    return false;
                }
            }
        }
        for (Integer mapCfgId : team.getMapCfgIds()) {
            if (mapCfgId == null || mapCfgId <= 0 || MapConfigs.get(mapCfgId) == null) {
                log.warn("MatchService 队伍校验失败：地图配置无效，teamId={}, mapCfgId={}",
                        team.getTeamId(), mapCfgId);
                return false;
            }
        }
        return true;
    }
}
