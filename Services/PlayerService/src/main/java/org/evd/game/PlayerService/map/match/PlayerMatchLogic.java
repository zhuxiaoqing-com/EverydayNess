package org.evd.game.PlayerService.map.match;

import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.dbDef.db.bean.DBMatchContext;
import org.evd.game.PlayerService.dbDef.db.bean.DBRoleMapData;
import org.evd.game.PlayerService.dbDef.db.table.DBRoleMapDataTable;
import org.evd.game.PlayerService.map.PlayerMapLogic;
import org.evd.game.PlayerService.map.PlayerMapState;
import org.evd.game.PlayerService.map.PlayerMapStateLogic;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.annotation.actor.Actor;
import java.util.ArrayList;
import org.evd.game.common.constant.MatchConst;
import org.evd.game.common.proto.C2S_Match;
import org.evd.game.common.proto.MatchMsgId;
import org.evd.game.common.proto.S2C_CancelMatch;
import org.evd.game.common.proto.S2C_Match;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.MatchService.MatchRpcProxy;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MatchPlayerEnterMapParam;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerEnterParam;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/** 玩家匹配生命周期：入队、取消、超时校正及匹配结果进图。 */
@Slf4j
@Actor
public final class PlayerMatchLogic {
    /** 单人匹配入口：校正旧快照、占用匹配状态，再提交 MatchService。 */
    public boolean startSingleMatch(ClientSessionRef session, C2S_Match request) {
        if (session == null || session.getPlayerId() <= 0L) {
            log.warn("PlayerService 发起单人匹配失败：玩家会话非法，session={}", session);
            return false;
        }
        long playerId = session.getPlayerId();
        if (request == null) {
            log.warn("PlayerService 发起单人匹配失败：请求为空，playerId={}", playerId);
            pushStartResult(playerId, false, "匹配请求为空");
            return false;
        }
        if (!owner().sessionManager().hasOnlinePlayer(playerId)) {
            log.warn("PlayerService 发起单人匹配失败：玩家不在线，playerId={}", playerId);
            pushStartResult(playerId, false, "玩家不在线");
            return false;
        }
        PlayerMapState currentState = stateLogic().getState(playerId);
        if (isMatchingState(currentState)) {
            log.warn("PlayerService 发起单人匹配忽略：玩家已经在匹配中，playerId={}, state={}",
                    playerId, currentState);
            return false;
        }

        if (!stateLogic().enter(playerId, PlayerMapState.MATCHING)) {
            log.warn("PlayerService 发起单人匹配失败：玩家当前状态不允许匹配，playerId={}, state={}",
                    playerId, stateLogic().getState(playerId));
            pushStartResult(playerId, false, "玩家当前不能匹配");
            return false;
        }
        DBMatchContext context = getMatchContext(playerId);
        if (isActiveMatch(context)) {
            log.info("PlayerService 发起单人匹配前清理旧匹配，playerId={}, matchType={}, matchStartMill={}",
                    playerId, context.getMatchType(), context.getMatchStartMill());
            if (!cancelAndClearMatchContext(playerId, false, false)) {
                log.error("PlayerService 发起单人匹配前取消旧匹配失败，保留原匹配状态: playerId={}", playerId);
                stateLogic().exit(playerId, PlayerMapState.MATCHING);
                pushStartResult(playerId, false, "取消旧匹配失败");
                return false;
            }
        }

        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        roleMapData.setMatchContext(createMatchContext(request));
        RpcResult<Boolean> result = MatchRpcProxy.callMatch(
                MatchConst.getMatchCallPoint(), createMatchRequest(playerId, request));
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.warn("PlayerService 发起单人匹配失败：MatchService RPC 或业务拒绝，playerId={}, matchType={}, errorCode={}, message={}",
                    playerId, request.getMatchType(), result.getErrorCode(), result.getErrorMessage());
            clearMatchContext(playerId);
            stateLogic().exit(playerId, PlayerMapState.MATCHING);
            pushStartResult(playerId, false, "匹配失败");
            return false;
        }
        log.info("PlayerService 单人匹配请求已提交: playerId={}, matchType={}, mapCfgIds={}",
                playerId, request.getMatchType(), request.getMapCfgIdsList());
        pushStartResult(playerId, true, "匹配已开始");

        return true;
    }

    /** 客户端取消匹配：先取消远端队列，再清理本地匹配状态并通知客户端。 */
    public void cancelMatch(ClientSessionRef session) {
        if (session == null || session.getPlayerId() <= 0L) {
            log.warn("PlayerService 取消匹配失败：玩家会话非法，session={}", session);
            return;
        }
        boolean success = cancelAndClearMatchContext(session.getPlayerId(), true, true);
        if (!success) {
            // 主动取消的 不管怎么样 都发一个协议，让客户端关闭匹配框
            log.warn("PlayerService 主动取消匹配失败，仍通知客户端关闭匹配框: playerId={}",
                    session.getPlayerId());
            push(session.getPlayerId(), MatchMsgId.S2C_MATCH_CANCEL_VALUE,
                    S2C_CancelMatch.newBuilder().setSuccess(true).setMessage("已取消匹配").build());
        }
    }

    /** 下线时先按有效匹配快照取消远端队列，再清理本地匹配状态。 */
    public void cancelOnOffline(long playerId) {
        cancelAndClearMatchContext(playerId, true, false);
    }

    /** 接收 MatchService 的匹配结果；重新匹配取消只清理旧队列，不结束当前匹配状态。 */
    public void onMatchResult(long playerId, boolean success, boolean isTeamMatch) {
        exitMatchingState(playerId);

        if (isActiveMatch(getMatchContext(playerId))) {
            clearMatchContext(playerId);
        }
        if (!isTeamMatch) {
            push(playerId, MatchMsgId.S2C_MATCH_START_VALUE,
                    S2C_Match.newBuilder().setSuccess(success).build());
        }
    }

    /** 匹配成功进入地图：匹配状态已经由 MatchService 清理，再进入地图转场。 */
    public void matchEnterMap(long playerId, SMapInfo targetInfo,
                              MatchPlayerEnterMapParam matchParam) {
        if (targetInfo == null || targetInfo.getMapCfgId() <= 0 || targetInfo.getGroupId() <= 0L) {
            log.warn("PlayerService 匹配进图参数非法: playerId={}, targetInfo={}", playerId, targetInfo);
            return;
        }
        if (!owner().getActor(PlayerMapLogic.class).startTransfer(
                playerId, targetInfo, new SPlayerEnterParam(matchParam))) {
            log.warn("PlayerService 匹配进图转场启动失败: playerId={}, targetInfo={}", playerId, targetInfo);
        }
    }




    /**
     * 取消远端匹配并清理本地匹配上下文。
     *
     * @param clearMatchingState 远端取消成功后是否退出玩家匹配状态
     * @param notifyCancelResult 是否通知客户端取消匹配结果
     */
    private boolean cancelAndClearMatchContext(long playerId,
                                               boolean clearMatchingState,
                                               boolean notifyCancelResult) {
        PPlayerOnline online = owner().sessionManager().get(playerId);
        String userId = online == null ? "" : online.getUserId();
        RpcResult<Boolean> result = MatchRpcProxy.callCancel(
                MatchConst.getMatchCallPoint(), playerId);
        if (!result.isSuccess()) {
            log.error("PlayerService 发送取消远端匹配消息失败，userId={}, playerId={}, errorCode={}, message={}",
                    userId, playerId, result.getErrorCode(), result.getErrorMessage());
            return false;
        }
        if (!Boolean.TRUE.equals(result.getValue())) {
            log.warn("PlayerService 远程匹配中不存在玩家，继续清理本地匹配状态: userId={}, playerId={}",
                    userId, playerId);
        }
        clearMatchContext(playerId);
        if (clearMatchingState) {
            exitMatchingState(playerId);
        }
        if (notifyCancelResult) {
            push(playerId, MatchMsgId.S2C_MATCH_CANCEL_VALUE,
                    S2C_CancelMatch.newBuilder().setSuccess(true).setMessage("已取消匹配").build());
            log.info("PlayerService 取消匹配完成: userId={}, playerId={}", userId, playerId);
        }
        return true;
    }

    private DBMatchContext createMatchContext(C2S_Match request) {
        DBMatchContext context = new DBMatchContext();
        context.setMatchType(request.getMatchType());
        context.getMapCfgIds().addAll(request.getMapCfgIdsList());
        context.getDutyIds().addAll(request.getDutyIdsList());
        context.setMatchStartMill(Service.getTime());
        return context;
    }

    private SMatchRequest createMatchRequest(long playerId, C2S_Match request) {
        SMatchRequest matchRequest = new SMatchRequest(
                playerId, request.getMatchType(), request.getMapCfgIdsList(), owner().getCallPoint());
        matchRequest.setDutyIds(request.getDutyIdsList());
        return matchRequest;
    }

    private void exitMatchingState(long playerId) {
        PlayerMapState state = stateLogic().getState(playerId);
        if (!isMatchingState(state)) {
            return;
        }
        stateLogic().exit(playerId, state);
    }

    private DBMatchContext getMatchContext(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        return data == null ? null : data.getMatchContext();
    }

    private void clearMatchContext(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        if (data != null) {
            data.setMatchContext(new DBMatchContext());
        }
    }


    private boolean isMatchingState(PlayerMapState state) {
        return state == PlayerMapState.MATCHING || state == PlayerMapState.TEAM_MATCHING;
    }

    private boolean isActiveMatch(DBMatchContext context) {
        return context != null && context.getMatchStartMill() > 0L;
    }

    private void pushStartResult(long playerId, boolean success, String message) {
        push(playerId, MatchMsgId.S2C_MATCH_START_VALUE,
                S2C_Match.newBuilder().setSuccess(success).setMessage(message).build());
    }

    private void push(long playerId, int messageId, Message message) {
        RpcResult<Void> result = ConnServiceRpcProxy.callPushToPlayerId(
                playerId, ClientFrameChunk.wrap(messageId, message));
        if (!result.isSuccess()) {
            log.warn("PlayerService 通知客户端匹配结果失败: playerId={}, messageId={}, errorCode={}, message={}",
                    playerId, messageId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }

    private PlayerMapStateLogic stateLogic() {
        return owner().getActor(PlayerMapStateLogic.class);
    }
}
