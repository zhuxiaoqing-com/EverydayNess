package org.evd.game.TeamService;

import lombok.extern.slf4j.Slf4j;
import com.google.protobuf.Message;
import org.evd.game.TeamService.entity.TeamData;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import java.util.Collection;
import java.util.ArrayList;
import org.evd.game.common.constant.MatchConst;
import org.evd.game.common.constant.MatchType;
import org.evd.game.common.proto.S2C_TeamInfo;
import org.evd.game.common.proto.S2C_TeamMatchResult;
import org.evd.game.common.proto.S2C_TeamResult;
import org.evd.game.common.proto.TeamMsgId;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.MatchService.MatchRpcProxy;
import org.evd.game.common.serializeBean.MatchService.match.SMatchPlayer;
import org.evd.game.common.serializeBean.MatchService.match.SMatchTeamRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.TeamService.team.STeamMatchRequest;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 队伍生命周期、队员索引和组队匹配状态。 */
@Slf4j
@Actor
public final class TeamLogic {
    private static final int MAX_MEMBER_NUM = 5;

    private final AtomicLong teamId = new AtomicLong(System.currentTimeMillis() << 10);
    private final Map<Long, TeamData> teams = new HashMap<>();
    private final Map<Long, Long> playerTeamIds = new HashMap<>();
    private record PendingInvite(long teamId, CallPoint playerService) {
        private PendingInvite {
            playerService = playerService == null ? null : new CallPoint(playerService);
        }
    }

    private final Map<Long, PendingInvite> pendingInvites = new HashMap<>();
    /** 使断连前仍在等待返回的匹配请求失效。 */
    private long matchConnectionVersion;

    /** Match 断开只取消匹配，保留队伍成员及队长。 */
    public void onMatchServiceDisconnect(Collection<RegisteredService> services) {
        int affected = 0;
        for (RegisteredService service : services) {
            if (service.getServiceType() == ServiceType.MATCH
                    && service.getCallPoint().equals(MatchConst.getMatchCallPoint())) {
                matchConnectionVersion++;
                for (TeamData team : new ArrayList<>(teams.values())) {
                    if (team.isMatching()) {
                        team.setMatching(false);
                        affected++;
                        pushTeamInfo(team, true, "匹配服务断开，已取消组队匹配");
                    }
                }
            }
        }
        if (affected > 0) {
            log.info("TeamService 完成 MatchService 断开清理: affected={}", affected);
        }
    }

    /** PlayerService 断开时移除该服的队员，并使相关匹配和邀请失效。 */
    public void onPlayerServiceDisconnect(Collection<RegisteredService> services) {
        for (RegisteredService service : services) {
            if (service != null && service.getServiceType() == ServiceType.PLAYER) {
                onPlayerServiceDisconnect(service.getCallPoint());
            }
        }
    }

    /** 正常玩家下线时执行与服务断开相同的队伍状态收敛。 */
    public void onPlayerOffline(long playerId, CallPoint playerService) {
        if (playerId <= 0L || playerService == null) {
            return;
        }
        TeamData team = findTeam(playerId);
        if (team == null || !playerService.equals(team.getMemberService(playerId))) {
            pendingInvites.remove(playerId);
            return;
        }
        removeDisconnectedMembers(team, List.of(playerId), playerService);
    }

    private void onPlayerServiceDisconnect(CallPoint playerService) {
        if (playerService == null) {
            return;
        }
        for (TeamData team : new ArrayList<>(teams.values())) {
            List<Long> disconnectedMembers = team.getMemberIds().stream()
                    .filter(playerId -> playerService.equals(team.getMemberService(playerId)))
                    .toList();
            if (!disconnectedMembers.isEmpty()) {
                removeDisconnectedMembers(team, disconnectedMembers, playerService);
            }
        }
        pendingInvites.entrySet().removeIf(entry ->
                playerService.equals(entry.getValue().playerService()));
    }

    private void removeDisconnectedMembers(TeamData team, List<Long> playerIds,
                                           CallPoint playerService) {
        if (team.isMatching()) {
            cancelRemoteMatch(team);
        }
        clearPendingInvites(team.getTeamId());
        for (long playerId : playerIds) {
            team.removeMember(playerId);
            playerTeamIds.remove(playerId, team.getTeamId());
            pendingInvites.remove(playerId);
        }
        if (team.getMemberIds().isEmpty()) {
            teams.remove(team.getTeamId(), team);
            log.info("TeamService 玩家断线后解散空队伍: teamId={}, playerService={}, playerIds={}",
                    team.getTeamId(), playerService, playerIds);
            return;
        }
        if (!team.getMemberIds().contains(team.getLeaderId())) {
            team.setLeaderId(team.getMemberIds().getFirst());
        }
        pushTeamInfo(team, true, "队伍成员已下线");
        log.info("TeamService 玩家断线后清理队伍成员: teamId={}, playerService={}, playerIds={}, leaderId={}",
                team.getTeamId(), playerService, playerIds, team.getLeaderId());
    }

    private void cancelRemoteMatch(TeamData team) {
        RpcResult<Boolean> result = MatchRpcProxy.callCancelTeam(
                MatchConst.getMatchCallPoint(), team.getTeamId(), team.getLeaderId());
        if (!result.isSuccess()) {
            log.warn("TeamService 玩家断线取消远端组队匹配失败，继续清理本地状态: teamId={}, errorCode={}, message={}",
                    team.getTeamId(), result.getErrorCode(), result.getErrorMessage());
        } else if (!Boolean.TRUE.equals(result.getValue())) {
            log.info("TeamService 玩家断线时 MatchService 中不存在组队匹配: teamId={}", team.getTeamId());
        }
        team.setMatching(false);
    }

    private void clearPendingInvites(long teamId) {
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().teamId() == teamId);
    }

    public boolean create(long playerId, CallPoint playerService) {
        if (playerId <= 0L || playerService == null || playerTeamIds.containsKey(playerId)) {
            return fail(playerId, "玩家已经在队伍中");
        }
        long newTeamId = teamId.incrementAndGet();
        TeamData team = new TeamData(newTeamId, playerId, playerService);
        teams.put(newTeamId, team);
        playerTeamIds.put(playerId, newTeamId);
        pushTeamInfo(team, true, "队伍创建成功");
        return true;
    }

    public boolean invite(long playerId, long targetPlayerId) {
        TeamData team = findLeaderTeam(playerId);
        if (team == null) {
            return fail(playerId, "只有队长可以邀请玩家");
        }
        if (team.isMatching()) {
            return fail(playerId, "匹配中不能邀请玩家");
        }
        if (targetPlayerId <= 0L || targetPlayerId == playerId
                || playerTeamIds.containsKey(targetPlayerId)) {
            return fail(playerId, "目标玩家当前不能加入队伍");
        }
        if (team.getMemberIds().size() >= MAX_MEMBER_NUM) {
            return fail(playerId, "队伍人数已满");
        }
        CallPoint targetPlayerService = null;
        try {
            ActorAddress targetActor = owner().getMessageLocationSender()
                    .getOrQuery(ActorId.player(targetPlayerId));
            if (targetActor != null) {
                targetPlayerService = targetActor.getCallPoint();
            }
        } catch (RuntimeException e) {
            log.warn("TeamService 查询被邀请玩家所属 PlayerService 失败: targetPlayerId={}",
                    targetPlayerId, e);
        }
        pendingInvites.put(targetPlayerId, new PendingInvite(team.getTeamId(), targetPlayerService));
        pushResult(playerId, true, "邀请已发送");
        pushResult(targetPlayerId, true, "收到队伍邀请，teamId=" + team.getTeamId());
        return true;
    }

    public boolean accept(long playerId, long teamId, CallPoint playerService) {
        PendingInvite pendingInvite = pendingInvites.get(playerId);
        Long invitedTeamId = pendingInvite == null ? null : pendingInvite.teamId();
        TeamData team = teams.get(teamId);
        if (invitedTeamId == null || invitedTeamId != teamId || team == null) {
            return fail(playerId, "队伍邀请不存在或已失效");
        }
        if (playerService == null || playerTeamIds.containsKey(playerId)) {
            pendingInvites.remove(playerId);
            return fail(playerId, "玩家已经在队伍中");
        }
        if (team.isMatching() || team.getMemberIds().size() >= MAX_MEMBER_NUM) {
            pendingInvites.remove(playerId);
            return fail(playerId, "队伍当前不能加入");
        }
        team.addMember(playerId, playerService);
        playerTeamIds.put(playerId, teamId);
        pendingInvites.remove(playerId);
        pushTeamInfo(team, true, "玩家加入队伍");
        return true;
    }

    public boolean leave(long playerId) {
        TeamData team = findTeam(playerId);
        if (team == null) {
            return fail(playerId, "玩家不在队伍中");
        }
        if (team.isMatching()) {
            return fail(playerId, "请先取消匹配");
        }
        team.removeMember(playerId);
        playerTeamIds.remove(playerId);
        if (team.getMemberIds().isEmpty()) {
            teams.remove(team.getTeamId());
            pushResult(playerId, true, "已退出队伍");
            return true;
        }
        if (team.getLeaderId() == playerId) {
            team.setLeaderId(team.getMemberIds().getFirst());
        }
        pushTeamInfo(team, true, "玩家退出队伍");
        pushResult(playerId, true, "已退出队伍");
        return true;
    }

    public boolean kick(long playerId, long targetPlayerId) {
        TeamData team = findLeaderTeam(playerId);
        if (team == null) {
            return fail(playerId, "只有队长可以踢出玩家");
        }
        if (team.isMatching() || targetPlayerId == playerId
                || !team.getMemberDutyIds().containsKey(targetPlayerId)) {
            return fail(playerId, "目标玩家当前不能被踢出");
        }
        team.removeMember(targetPlayerId);
        playerTeamIds.remove(targetPlayerId);
        pushTeamInfo(team, true, "玩家已被踢出队伍");
        pushResult(targetPlayerId, true, "你已被踢出队伍");
        return true;
    }

    public boolean disband(long playerId) {
        TeamData team = findLeaderTeam(playerId);
        if (team == null) {
            return fail(playerId, "只有队长可以解散队伍");
        }
        if (team.isMatching()) {
            return fail(playerId, "请先取消匹配");
        }
        for (long memberId : team.getMemberIds()) {
            playerTeamIds.remove(memberId);
            pushResult(memberId, true, "队伍已解散");
        }
        teams.remove(team.getTeamId());
        return true;
    }

    public boolean match(long playerId, STeamMatchRequest request) {
        TeamData team = findLeaderTeam(playerId);
        if (team == null) {
            return fail(playerId, "只有队长可以发起匹配");
        }
        if (team.isMatching()) {
            return fail(playerId, "队伍已经在匹配中");
        }
        if (!validMatchRequest(request)) {
            return fail(playerId, "组队匹配参数非法");
        }
        SMatchTeamRequest matchRequest = new SMatchTeamRequest();
        matchRequest.setTeamId(team.getTeamId());
        matchRequest.setMatchType(request.getMatchType());
        matchRequest.setMapCfgIds(request.getMapCfgIds());
        List<SMatchPlayer> members = team.toMatchPlayers();
        if (!request.getDutyIds().isEmpty() && !members.isEmpty()) {
            members.getFirst().setDutyIds(request.getDutyIds());
        }
        matchRequest.setMembers(members);

        long connectionVersion = matchConnectionVersion;
        RpcResult<Boolean> result = MatchRpcProxy.callTeamMatch(
                MatchConst.getMatchCallPoint(), matchRequest);
        if (connectionVersion != matchConnectionVersion) {
            return fail(playerId, "匹配服务已断开，组队匹配已取消");
        }
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.warn("TeamService 发起组队匹配失败: teamId={}, errorCode={}, message={}",
                    team.getTeamId(), result.getErrorCode(), result.getErrorMessage());
            return fail(playerId, "组队匹配失败");
        }
        team.setMatching(true);
        pushTeamInfo(team, true, "组队匹配已开始");
        return true;
    }

    public boolean cancelMatch(long playerId) {
        TeamData team = findLeaderTeam(playerId);
        if (team == null || !team.isMatching()) {
            return fail(playerId, "队伍当前未在匹配");
        }
        RpcResult<Boolean> result = MatchRpcProxy.callCancelTeam(
                MatchConst.getMatchCallPoint(), team.getTeamId(), team.getLeaderId());
        if (!result.isSuccess()) {
            log.warn("TeamService 取消组队匹配失败: playerId={}, errorCode={}, message={}",
                    playerId, result.getErrorCode(), result.getErrorMessage());
            return fail(playerId, "取消组队匹配失败");
        }
        if (!Boolean.TRUE.equals(result.getValue())) {
            log.info("TeamService 取消组队匹配幂等完成：MatchService 中不存在队伍，teamId={}, playerId={}",
                    team.getTeamId(), playerId);
            team.setMatching(false);
            pushTeamInfo(team, true, "组队匹配已取消");
        }
        return true;
    }

    /** MatchService 主动取消组队匹配时，只清理队伍匹配状态。 */
    public boolean cancelMatchState(long teamId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.isMatching()) {
            log.info("TeamService 取消组队匹配幂等完成：队伍当前未在匹配，teamId={}", teamId);
            return true;
        }
        team.setMatching(false);
        pushTeamInfo(team, true, "组队匹配已取消");
        return true;
    }

    public void matchResult(long teamId, int matchType, boolean success, SMapInfo mapInfo) {
        TeamData team = teams.get(teamId);
        if (team == null) {
            log.warn("TeamService 收到未知队伍的匹配结果: teamId={}, matchType={}, success={}",
                    teamId, matchType, success);
            return;
        }
        team.setMatching(false);
        if (!success || mapInfo == null) {
            pushMatchResult(team, false, matchType, null, "组队匹配失败");
            pushTeamInfo(team, false, "组队匹配失败");
            return;
        }
        pushMatchResult(team, true, matchType, mapInfo, "组队匹配成功");
        pushTeamInfo(team, true, "组队匹配成功");
    }

    private boolean validMatchRequest(STeamMatchRequest request) {
        return request != null && MatchType.isValid(request.getMatchType())
                && request.getMapCfgIds() != null && !request.getMapCfgIds().isEmpty()
                && request.getMapCfgIds().stream().allMatch(id -> id != null && id > 0);
    }

    private TeamData findTeam(long playerId) {
        Long teamId = playerTeamIds.get(playerId);
        return teamId == null ? null : teams.get(teamId);
    }

    private TeamData findLeaderTeam(long playerId) {
        TeamData team = findTeam(playerId);
        return team != null && team.getLeaderId() == playerId ? team : null;
    }

    private boolean fail(long playerId, String message) {
        log.warn("TeamService 操作失败: playerId={}, message={}", playerId, message);
        if (playerId > 0L) {
            pushResult(playerId, false, message);
        }
        return false;
    }

    private void pushTeamInfo(TeamData team, boolean success, String message) {
        S2C_TeamInfo info = S2C_TeamInfo.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .setTeamId(team.getTeamId())
                .setLeaderId(team.getLeaderId())
                .addAllMemberIds(team.getMemberIds())
                .setMatching(team.isMatching())
                .build();
        for (long memberId : team.getMemberIds()) {
            push(memberId, TeamMsgId.S2C_TEAM_INFO_VALUE, info);
        }
    }

    private void pushResult(long playerId, boolean success, String message) {
        push(playerId, TeamMsgId.S2C_TEAM_RESULT_VALUE,
                S2C_TeamResult.newBuilder().setSuccess(success).setMessage(message).build());
    }

    private void pushMatchResult(TeamData team, boolean success, int matchType,
                                 SMapInfo mapInfo, String message) {
        S2C_TeamMatchResult.Builder builder = S2C_TeamMatchResult.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .setMatchType(matchType);
        if (mapInfo != null) {
            builder.setSceneId(mapInfo.getSceneId())
                    .setMapCfgId(mapInfo.getMapCfgId())
                    .setGroupId(mapInfo.getGroupId());
        }
        S2C_TeamMatchResult result = builder.build();
        for (long memberId : team.getMemberIds()) {
            push(memberId, TeamMsgId.S2C_TEAM_MATCH_RESULT_VALUE, result);
        }
    }

    private void push(long playerId, int messageId, Message message) {
        RpcResult<Void> result = ConnServiceRpcProxy.callPushToPlayerId(
                playerId, ClientFrameChunk.wrap(messageId, message));
        if (!result.isSuccess()) {
            log.warn("TeamService 推送队伍消息失败: playerId={}, messageId={}, errorCode={}, message={}",
                    playerId, messageId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    private TeamService owner() {
        return Service.getCurrent(TeamService.class);
    }
}
