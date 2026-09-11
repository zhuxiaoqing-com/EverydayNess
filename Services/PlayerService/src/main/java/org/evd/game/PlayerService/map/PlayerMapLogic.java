package org.evd.game.PlayerService.map;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.dbDef.db.bean.DBMapInfo;
import org.evd.game.PlayerService.dbDef.db.bean.DBRoleMapData;
import org.evd.game.PlayerService.dbDef.db.bean.DBTransferContext;
import org.evd.game.PlayerService.dbDef.db.table.DBRoleMapDataTable;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.constant.MapConst;
import org.evd.game.common.constant.MatchConst;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.LocationService.LocationServiceRpcProxy;
import org.evd.game.common.proxy.MatchService.MatchRpcProxy;
import org.evd.game.common.proxy.OnlineService.OnlineSessionRpcProxy;
import org.evd.game.common.proxy.SceneManagerService.SceneManagerRpcProxy;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerEnterParam;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MatchPlayerEnterMapParam;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapSimpleData;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRequest;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.common.proto.C2S_ReadyEnterMap;
import org.evd.game.common.proto.C2S_Match;
import org.evd.game.common.proto.MapMsgId;
import org.evd.game.common.proto.MatchMsgId;
import org.evd.game.common.proto.S2C_CancelMatch;
import org.evd.game.common.proto.S2C_Match;
import org.evd.game.common.proto.S2C_ReadyEnterMap;

/** PlayerService 独立的地图转场逻辑。 */
@Slf4j
@Actor
public final class PlayerMapLogic {
    private static final int LOGIN_MAP_CFG_ID = 1;
    private static final long LOGIN_GROUP_ID = 0L;
    private static final long LOGIN_STATE_WAIT_MILL = 2_000L;

    /** 登录进入默认地图；已有状态即将超时时短暂等待，避免本次登录无意义失败。 */
    public void enterMapOnLogin(long playerId) {
        long remainingMill = stateLogic().getRemainingMill(playerId);
        if (remainingMill > 0L && remainingMill <= LOGIN_STATE_WAIT_MILL) {
            log.info("PlayerService 登录等待玩家地图状态超时: playerId={}, remainingMill={}",
                    playerId, remainingMill);
            Service.getCurrent().sleep(remainingMill);
            if (!owner().sessionManager().hasOnlinePlayer(playerId)) {
                log.warn("PlayerService 登录等待地图状态期间玩家已离线: playerId={}", playerId);
                return;
            }
        }
        enterMap(playerId, new SMapInfo(0L, LOGIN_MAP_CFG_ID, LOGIN_GROUP_ID), null);
    }

    /** 普通地图进入，不涉及匹配状态。 */
    public boolean enterMap(long playerId, SMapInfo targetInfo,
                            SPlayerEnterParam enterParam) {
        return startTransfer(playerId, targetInfo, enterParam);
    }

    /** 单人匹配入口：先占用玩家匹配状态，再提交 MatchService。 */
    public void startSingleMatch(ClientSessionRef session, C2S_Match request) {
        if (session == null || session.getPlayerId() <= 0L) {
            log.warn("PlayerService 发起单人匹配失败：玩家会话非法，session={}", session);
            return;
        }
        long playerId = session.getPlayerId();
        if (request == null) {
            log.warn("PlayerService 发起单人匹配失败：请求为空，playerId={}", playerId);
            pushResult(MatchMsgId.S2C_MATCH_START_VALUE, playerId, false, "匹配请求为空");
            return;
        }
        if (!owner().sessionManager().hasOnlinePlayer(playerId)) {
            log.warn("PlayerService 发起单人匹配失败：玩家不在线，playerId={}", playerId);
            pushResult(MatchMsgId.S2C_MATCH_START_VALUE, playerId, false, "玩家不在线");
            return;
        }
        CallPoint matchService = MatchConst.getMatchCallPoint();
        if (!stateLogic().enter(playerId, PlayerMapState.MATCHING)) {
            log.warn("PlayerService 发起单人匹配失败：玩家当前状态不允许匹配，playerId={}, state={}",
                    playerId, stateLogic().getState(playerId));
            pushResult(MatchMsgId.S2C_MATCH_START_VALUE, playerId, false, "玩家当前不能匹配");
            return;
        }

        SMatchRequest matchRequest = new SMatchRequest(
                playerId, request.getMatchType(), request.getMapCfgIdsList(), owner().getCallPoint());
        matchRequest.setDutyIds(request.getDutyIdsList());
        RpcResult<Boolean> result = MatchRpcProxy.callMatch(matchService, matchRequest);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.warn("PlayerService 发起单人匹配失败：MatchService 拒绝请求，playerId={}, matchType={}, errorCode={}, message={}",
                    playerId, request.getMatchType(), result.getErrorCode(), result.getErrorMessage());
            if (!stateLogic().exit(playerId, PlayerMapState.MATCHING)) {
                log.error("PlayerService 回滚单人匹配状态失败: playerId={}", playerId);
            }
            pushResult(MatchMsgId.S2C_MATCH_START_VALUE, playerId, false, "匹配失败");
            return;
        }
        log.info("PlayerService 单人匹配请求已提交: playerId={}, matchType={}, mapCfgIds={}",
                playerId, request.getMatchType(), request.getMapCfgIdsList());
        pushResult(MatchMsgId.S2C_MATCH_START_VALUE, playerId, true, "匹配已开始");
    }

    /** 单人匹配取消入口；组队取消时由 MatchService 根据 playerId 反查整队。 */
    public void cancelMatch(ClientSessionRef session) {
        if (session == null || session.getPlayerId() <= 0L) {
            log.warn("PlayerService 取消匹配失败：玩家会话非法，session={}", session);
            return;
        }
        long playerId = session.getPlayerId();
        RpcResult<Boolean> result = MatchRpcProxy.callCancel(MatchConst.getMatchCallPoint(), playerId);
        if (!result.isSuccess()) {
            log.warn("PlayerService 取消匹配失败：MatchService RPC 调用失败，playerId={}, errorCode={}, message={}",
                    playerId, result.getErrorCode(), result.getErrorMessage());
            pushResult(MatchMsgId.S2C_MATCH_CANCEL_VALUE, playerId, false, "取消匹配失败");
            return;
        }
        if (!Boolean.TRUE.equals(result.getValue())) {
            log.warn("PlayerService 取消匹配失败：玩家不在 MatchService 匹配队列，playerId={}", playerId);
            if (stateLogic().isIn(playerId, PlayerMapState.MATCHING)) {
                stateLogic().exit(playerId, PlayerMapState.MATCHING);
            }
            pushResult(MatchMsgId.S2C_MATCH_CANCEL_VALUE, playerId, false, "玩家当前未在匹配");
            return;
        }
        log.info("PlayerService 取消匹配成功: playerId={}", playerId);
    }

    /** 匹配成功进入地图：先退出匹配状态，再进入地图状态。 */
    public void matchEnterMap(long playerId, SMapInfo targetInfo,
                              MatchPlayerEnterMapParam matchParam) {
        if (targetInfo == null || targetInfo.getMapCfgId() <= 0 || targetInfo.getGroupId() <= 0L) {
            log.warn("PlayerService 匹配进图参数非法: playerId={}, targetInfo={}", playerId, targetInfo);
            return;
        }
        if (!startTransfer(playerId, targetInfo, new SPlayerEnterParam(matchParam))) {
            log.warn("PlayerService 匹配进图转场启动失败: playerId={}, targetInfo={}",
                    playerId, targetInfo);
        }
    }

    /** MatchService 已确定匹配结果后，只清理匹配状态，不发送客户端取消协议。 */
    public void clearMatchState(long playerId) {
        PlayerMapState currentState = stateLogic().getState(playerId);
        if (currentState != PlayerMapState.MATCHING && currentState != PlayerMapState.TEAM_MATCHING) {
            return;
        }
        stateLogic().exit(playerId, currentState);
    }

    private void pushResult(int messageId, long playerId, boolean success, String message) {
        RpcResult<Void> result = ConnServiceRpcProxy.callPushToPlayerId(
                playerId, ClientFrameChunk.wrap(messageId, messageId == MatchMsgId.S2C_MATCH_START_VALUE
                        ? S2C_Match.newBuilder().setSuccess(success).setMessage(message).build()
                        : S2C_CancelMatch.newBuilder().setSuccess(success).setMessage(message).build()));
        if (!result.isSuccess()) {
            log.warn("PlayerService 通知客户端匹配结果失败: playerId={}, errorCode={}, message={}",
                    playerId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    public boolean startTransfer(long playerId, SMapInfo targetInfo,
                                 SPlayerEnterParam enterParam) {
        int mapCfgId = targetInfo.getMapCfgId();
        long groupId = targetInfo.getGroupId();
        PlayerService owner = owner();
        if(!owner.sessionManager().hasOnlinePlayer(playerId)) {
            log.warn("PlayerService 玩家不在线，忽略地图进入请求: playerId={}", playerId);
            return false;
        }
        DBRoleMapData roleMapData = getOrCreateRoleMapData(playerId);
        if (!stateLogic().enter(playerId, PlayerMapState.ENTER_MAP)) {
            log.warn("PlayerService 玩家当前状态不允许进入地图: playerId={}, state={}",
                    playerId, stateLogic().getState(playerId));
            return false;
        }
        DBTransferContext oldTransferContext = roleMapData.getTransferContext();
        DBMapInfo oldTarget = oldTransferContext == null ? null : oldTransferContext.getTargetInfo();
        if (oldTarget != null && oldTarget.getMapCfgId() > 0) {
            log.info("PlayerService 清理未完成地图转场: playerId={}, transferId={}, mapCfgId={}, groupId={}",
                    playerId, oldTransferContext.getTransferId(), oldTarget.getMapCfgId(), oldTarget.getGroupId());
            removeFromScene(playerId, oldTarget);
        }
        roleMapData.setTransferContext(new DBTransferContext());

        long transferId = Service.getTime();
        DBMapInfo currMapInfo = roleMapData.getCurrMapInfo();
        /*if (currMapInfo == null || currMapInfo.getMapCfgId() <= 0) {
            //currMapInfo = roleMapData.getOldCurrMapInfo();
        }*/
        SMapInfo oldMapInfo = currMapInfo == null
                ? new SMapInfo()
                : toCommon(currMapInfo);
        targetInfo = new SMapInfo(targetInfo);
        DBTransferContext transferContext = new DBTransferContext();
        transferContext.setTransferId(transferId);
        transferContext.setStart(true);
        transferContext.setOldMapInfo(toDb(oldMapInfo));
        transferContext.setTargetInfo(toDb(targetInfo));
        roleMapData.setTransferContext(transferContext);
        log.info("PlayerService 开始地图转场: playerId={}, transferId={}, oldSceneId={}, oldMapCfgId={}, oldGroupId={}, targetMapCfgId={}, targetGroupId={}",
                playerId, transferId, oldMapInfo.getSceneId(), oldMapInfo.getMapCfgId(), oldMapInfo.getGroupId(),
                mapCfgId, groupId);

        /*
         * 以后启用 Location 锁时，在这里锁住旧的 MAP_PLAYER 地址：
         * ActorAddress oldStageActorAddress = ...;
         * LocationServiceRpcProxy.sendLock(null, ActorId.mapPlayer(playerId), oldStageActorAddress, 30_000);
         */

        SPlayerMapSimpleData simpleData = dataLogic().getSimpleData(playerId);
        PlayerEnterRequest request = new PlayerEnterRequest(simpleData, transferId, owner.getCallPoint(),
                oldMapInfo, targetInfo, enterParam);
        CallPoint sceneManager = MapConst.getSceneManagerCallPoint(mapCfgId);
        RpcResult<Boolean> result = SceneManagerRpcProxy.callEnterMap(sceneManager, request);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.error("PlayerService 发送地图转场请求失败: playerId={}, transferId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                    playerId, transferId, mapCfgId, groupId, result.getErrorCode(), result.getErrorMessage());
            roleMapData.setTransferContext(new DBTransferContext());
            stateLogic().exit(playerId, PlayerMapState.ENTER_MAP);
            return false;
        }
        log.info("PlayerService SceneManager 已接受地图转场: playerId={}, transferId={}, mapCfgId={}, groupId={}",
                playerId, transferId, mapCfgId, groupId);
        return true;
    }

    /** 取消匹配时只清理匹配状态，不触碰玩家当前地图。 */
    public boolean cancelMatch(long playerId) {
        PlayerMapState currentState = stateLogic().getState(playerId);
        if (currentState != PlayerMapState.MATCHING && currentState != PlayerMapState.TEAM_MATCHING) {
            pushResult(MatchMsgId.S2C_MATCH_CANCEL_VALUE, playerId, false, "玩家当前未在匹配");
            return false;
        }
        boolean success = stateLogic().exit(playerId, currentState);
        pushResult(MatchMsgId.S2C_MATCH_CANCEL_VALUE, playerId, success,
                success ? "已取消匹配" : "取消匹配失败");
        return success;
    }

    /** Stage 完成旧场景退出后调用，通知客户端开始加载目标地图。 */
    public boolean readyEnterMap(long playerId, long transferId, SMapInfo targetInfo) {
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBTransferContext context = roleMapData == null ? null : roleMapData.getTransferContext();
        if (context == null || context.getTransferId() <= 0L || context.getTransferId() != transferId
                || targetInfo == null || !stateLogic().isIn(playerId, PlayerMapState.ENTER_MAP)) {
            log.warn("PlayerService 地图转场上下文已失效，忽略 ReadyEnterMap: playerId={}, transferId={}",
                    playerId, transferId);
            return false;
        }
        context.setTargetInfo(toDb(targetInfo));
        stateLogic().exit(playerId, PlayerMapState.ENTER_MAP);

        PPlayerOnline online = owner().sessionManager().get(playerId);
        if (online == null || online.getGate() == null || online.getGateSessionId() <= 0L) {
            log.warn("PlayerService 玩家会话不存在，无法通知客户端加载地图: playerId={}, transferId={}",
                    playerId, transferId);
            roleMapData.setTransferContext(new DBTransferContext());
            return false;
        }
        S2C_ReadyEnterMap message = S2C_ReadyEnterMap.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
                .setTransferId(transferId)
                .setSceneId(targetInfo.getSceneId())
                .setMapCfgId(targetInfo.getMapCfgId())
                .setGroupId(targetInfo.getGroupId())
                .build();
        RpcResult<Void> result = ConnServiceRpcProxy.callPushToPlayerId(playerId,
                ClientFrameChunk.wrap(MapMsgId.S2C_MAP_READY_ENTER_MAP_VALUE, message));
        if (!result.isSuccess()) {
            log.warn("PlayerService 通知客户端加载地图失败: playerId={}, transferId={}, errorCode={}, message={}",
                    playerId, transferId, result.getErrorCode(), result.getErrorMessage());
            roleMapData.setTransferContext(new DBTransferContext());
            return false;
        }
        log.info("PlayerService 完成地图预进入并通知客户端: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}",
                playerId, transferId, targetInfo.getSceneId(), targetInfo.getMapCfgId(), targetInfo.getGroupId());
        return true;
    }

    /** Stage 完成退出后通知 PlayerService 清理当前地图。 */
    public void onExitMap(long playerId, long sceneId, ActorAddress stageActorAddress) {
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBMapInfo current = roleMapData == null ? null : roleMapData.getCurrMapInfo();
        if (current == null || current.getSceneId() != sceneId) {
            log.warn("PlayerService 收到玩家退出地图通知，但当前地图不匹配: playerId={}, sceneId={}, current={}",
                    playerId, sceneId, current);
            return;
        }
        log.info("PlayerService 清理玩家当前地图: playerId={}, sceneId={}, mapCfgId={}, groupId={}",
                playerId, sceneId, current.getMapCfgId(), current.getGroupId());
        if (stageActorAddress == null) {
            log.error("PlayerService 玩家退出地图缺少 StageActorAddress: playerId={}, sceneId={}",
                    playerId, sceneId);
            throw new IllegalStateException("玩家退出地图缺少 StageActorAddress: playerId=" + playerId
                    + ", sceneId=" + sceneId);
        }
        RpcResult<Void> removeResult = LocationServiceRpcProxy.sendRemove(
                null, ActorId.mapPlayer(playerId), stageActorAddress);
        if (!removeResult.isSuccess()) {
            log.warn("PlayerService 清理玩家 StageActorAddress 失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, removeResult.getErrorCode(), removeResult.getErrorMessage());
        }
        PPlayerOnline online = owner().sessionManager().get(playerId);
        owner().removeStageActorAddress(playerId);
        if (online != null) {
            RpcResult<Void> connRemoveResult = ConnServiceRpcProxy.sendRemoveStageActorAddress(
                    online.getGate(), playerId);
            if (!connRemoveResult.isSuccess()) {
                log.warn("PlayerService 通知 ConnService 删除 StageActorAddress 失败: playerId={}, sceneId={}, errorCode={}, message={}",
                        playerId, sceneId, connRemoveResult.getErrorCode(), connRemoveResult.getErrorMessage());
            }
        }
        RpcResult<Void> onlineRemoveResult = OnlineSessionRpcProxy.sendRemoveStageActorAddress(
                null, playerId);
        if (!onlineRemoveResult.isSuccess()) {
            log.warn("PlayerService 通知 OnlineService 删除 StageActorAddress 失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, onlineRemoveResult.getErrorCode(), onlineRemoveResult.getErrorMessage());
        }
        roleMapData.setOldCurrMapInfo(new DBMapInfo(current));
        roleMapData.setCurrMapInfo(new DBMapInfo());
    }

    /** 客户端加载完成后，调用 Stage 让 SceneBattle 正式接纳玩家。 */
    public void confirmReadyEnterMap(ClientSessionRef session, C2S_ReadyEnterMap request) {
        long playerId = session.getPlayerId();
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBTransferContext context = roleMapData == null ? null : roleMapData.getTransferContext();
        if (context == null || context.getTransferId() <= 0L || !context.getStart()
                || context.getTransferId() != request.getTransferId()
                || context.getTargetInfo().getSceneId() <= 0L) {
            log.warn("PlayerService 客户端 ReadyEnterMap 与当前转场不匹配: playerId={}, transferId={}",
                    playerId, request.getTransferId());
            return;
        }

        PPlayerOnline online = owner().sessionManager().get(playerId);
        if (online == null || !owner().sessionManager().isCurrent(
                online.getUserId(), playerId, session.getGate(), session.getSessionId())) {
            log.warn("PlayerService 客户端 ReadyEnterMap 会话已失效: playerId={}, transferId={}",
                    playerId, request.getTransferId());
            return;
        }

        DBMapInfo targetInfo = context.getTargetInfo();
        CallPoint sceneManager = MapConst.getSceneManagerCallPoint(targetInfo.getMapCfgId());
        RpcResult<CallPoint> stage = SceneManagerRpcProxy.callGetSceneStage(sceneManager, targetInfo.getSceneId());
        if (!stage.isSuccess() || stage.getValue() == null) {
            log.error("PlayerService 找不到目标场景 Stage: playerId={}, transferId={}, sceneId={}, errorCode={}, message={}",
                    playerId, request.getTransferId(), targetInfo.getSceneId(),
                    stage.getErrorCode(), stage.getErrorMessage());
            roleMapData.setTransferContext(new DBTransferContext());
            return;
        }
        SPlayerMapData playerData = dataLogic().getData(playerId);
        playerData.setGateActorAddress(online.getGateActorAddress());
        playerData.setPlayerActorAddress(online.getActorAddress());
        RpcResult<Void> result = StageServiceRpcProxy.sendEnterScene(stage.getValue(),
                targetInfo.getSceneId(), playerData);
        if (!result.isSuccess()) {
            log.error("PlayerService 发送 Stage 正式进入地图请求失败: playerId={}, transferId={}, sceneId={}, errorCode={}, message={}",
                    playerId, request.getTransferId(), targetInfo.getSceneId(),
                    result.getErrorCode(), result.getErrorMessage());
            roleMapData.setTransferContext(new DBTransferContext());
            return;
        }
        log.info("PlayerService 已发送 Stage 正式进入地图请求: playerId={}, transferId={}, sceneId={}",
                playerId, request.getTransferId(), targetInfo.getSceneId());
    }

    /** Stage 完成玩家进入并注册场景 Actor 后，完成地址注册并通知客户端。 */
    public boolean onEnterMap(long playerId, long transferId, SMapInfo targetInfo,
                              ActorAddress stageActorAddress) {
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBTransferContext context = roleMapData == null ? null : roleMapData.getTransferContext();
        if (context == null || context.getTransferId() <= 0L || context.getTransferId() != transferId
                || targetInfo == null || targetInfo.getSceneId() <= 0L || stageActorAddress == null) {
            log.warn("PlayerService 玩家进入地图回调与当前转场不匹配: playerId={}, transferId={}, sceneId={}",
                    playerId, transferId, targetInfo == null ? 0L : targetInfo.getSceneId());
            return false;
        }

        PPlayerOnline online = owner().sessionManager().get(playerId);
        if (online == null || online.getGate() == null || online.getGateSessionId() <= 0L) {
            log.warn("PlayerService 玩家进入地图回调时会话不存在: playerId={}, transferId={}",
                    playerId, transferId);
            return false;
        }

        roleMapData.setCurrMapInfo(toDb(targetInfo));
        roleMapData.setTransferContext(new DBTransferContext());
        owner().cacheStageActorAddress(playerId, stageActorAddress);
        RpcResult<Boolean> connAddResult = ConnServiceRpcProxy.callCacheStageActorAddress(
                online.getGate(), playerId, stageActorAddress);
        if (!connAddResult.isSuccess() || !Boolean.TRUE.equals(connAddResult.getValue())) {
            log.error("PlayerService 通知 ConnService 缓存 StageActorAddress 失败: playerId={}, transferId={}, sceneId={}, errorCode={}, message={}",
                    playerId, transferId, targetInfo.getSceneId(),
                    connAddResult.getErrorCode(), connAddResult.getErrorMessage());
            return false;
        }
        RpcResult<Boolean> onlineAddResult = OnlineSessionRpcProxy.callCacheStageActorAddress(
                null, playerId, stageActorAddress);
        if (!onlineAddResult.isSuccess() || !Boolean.TRUE.equals(onlineAddResult.getValue())) {
            log.error("PlayerService 通知 OnlineService 缓存 StageActorAddress 失败: playerId={}, transferId={}, sceneId={}, errorCode={}, message={}",
                    playerId, transferId, targetInfo.getSceneId(),
                    onlineAddResult.getErrorCode(), onlineAddResult.getErrorMessage());
            return false;
        }
        RpcResult<Boolean> addResult = LocationServiceRpcProxy.callAdd(
                null, ActorId.mapPlayer(playerId), stageActorAddress);
        if (!addResult.isSuccess() || !Boolean.TRUE.equals(addResult.getValue())) {
            log.error("PlayerService 注册玩家 StageActorAddress 失败: playerId={}, transferId={}, sceneId={}, errorCode={}, message={}",
                    playerId, transferId, targetInfo.getSceneId(),
                    addResult.getErrorCode(), addResult.getErrorMessage());
            return false;
        }

        /*
         * 以后启用 Location 锁时，正式进入地图并完成上面的地址注册后再解锁：
         * LocationServiceRpcProxy.sendUnlock(null, ActorId.mapPlayer(playerId), oldStageActorAddress, stageActorAddress);
         */
        log.info("PlayerService 玩家正式进入地图: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}",
                playerId, transferId, targetInfo.getSceneId(), targetInfo.getMapCfgId(), targetInfo.getGroupId());
        return true;
    }

    /** 玩家离线时从当前场景移除。 */
    public void leaveMap(long playerId) {
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        if (roleMapData == null) {
            return;
        }

        DBTransferContext transferContext = roleMapData.getTransferContext();
        if (transferContext != null) {
            DBMapInfo target = transferContext.getTargetInfo();
            if (target != null && target.getMapCfgId() > 0) {
                removeFromScene(playerId, target);
            }
            roleMapData.setTransferContext(new DBTransferContext());
        }

        DBMapInfo current = roleMapData.getCurrMapInfo();
        if (current != null && current.getMapCfgId() > 0) {
            removeFromScene(playerId, current);
        }
        PlayerMapState state = stateLogic().getState(playerId);
        if (state != PlayerMapState.NONE) {
            stateLogic().exit(playerId, state);
        }
    }

    private void removeFromScene(long playerId,
                                 DBMapInfo current) {
        CallPoint sceneManager = MapConst.getSceneManagerCallPoint(current.getMapCfgId());
        RpcResult<Boolean> result = SceneManagerRpcProxy.callExitMap(sceneManager, toCommon(current), playerId);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.warn("PlayerService 离开当前场景失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, current.getSceneId(), result.getErrorCode(), result.getErrorMessage());
        }
    }

    private DBRoleMapData getOrCreateRoleMapData(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        if (data != null) {
            return data;
        }
        data = new DBRoleMapData();
        data.setPlayerId(playerId);
        DBRoleMapDataTable.add(playerId, data, true);
        return data;
    }

    private DBMapInfo toDb(SMapInfo info) {
        if (info == null) {
            return new DBMapInfo();
        }
        DBMapInfo result =
                new DBMapInfo();
        result.setSceneId(info.getSceneId());
        result.setMapCfgId(info.getMapCfgId());
        result.setGroupId(info.getGroupId());
        return result;
    }

    private SMapInfo toCommon(DBMapInfo info) {
        return new SMapInfo(info.getSceneId(), info.getMapCfgId(), info.getGroupId());
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }

    private PlayerMapDataLogic dataLogic() {
        return owner().getActor(PlayerMapDataLogic.class);
    }

    private PlayerMapStateLogic stateLogic() {
        return owner().getActor(PlayerMapStateLogic.class);
    }
}
