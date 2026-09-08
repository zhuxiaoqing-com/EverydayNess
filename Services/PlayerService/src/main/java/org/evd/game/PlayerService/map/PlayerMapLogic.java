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
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.LocationService.LocationServiceRpcProxy;
import org.evd.game.common.proxy.OnlineService.OnlineSessionRpcProxy;
import org.evd.game.common.proxy.SceneManagerService.SceneManagerRpcProxy;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapSimpleData;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.common.proto.C2S_ReadyEnterMap;
import org.evd.game.common.proto.MsgId;
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
        startTransfer(playerId, LOGIN_MAP_CFG_ID, LOGIN_GROUP_ID);
    }

    /** 发起一次普通地图转场。 */
    public void enterMap(long playerId, int mapCfgId, long groupId) {
        startTransfer(playerId, mapCfgId, groupId);
    }

    private void startTransfer(long playerId, int mapCfgId, long groupId) {
        PlayerService owner = owner();
        if(!owner.sessionManager().hasOnlinePlayer(playerId)) {
            log.warn("PlayerService 玩家不在线，忽略地图进入请求: playerId={}", playerId);
            return;
        }
        DBRoleMapData roleMapData = getOrCreateRoleMapData(playerId);
        if (!stateLogic().enter(playerId, PlayerMapState.ENTER_MAP)) {
            log.warn("PlayerService 玩家当前状态不允许进入地图: playerId={}, state={}",
                    playerId, stateLogic().getState(playerId));
            return;
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
        SMapInfo targetInfo = new SMapInfo(0L, mapCfgId, groupId);
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
        SMapEnterRequest request = new SMapEnterRequest(simpleData, transferId, owner.getCallPoint(), oldMapInfo, targetInfo);
        CallPoint sceneManager = MapConst.getSceneManagerCallPoint(mapCfgId);
        RpcResult<Boolean> result = SceneManagerRpcProxy.callEnterMap(sceneManager, request);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.error("PlayerService 发送地图转场请求失败: playerId={}, transferId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                    playerId, transferId, mapCfgId, groupId, result.getErrorCode(), result.getErrorMessage());
            roleMapData.setTransferContext(new DBTransferContext());
            stateLogic().exit(playerId, PlayerMapState.ENTER_MAP);
            return;
        }
        log.info("PlayerService SceneManager 已接受地图转场: playerId={}, transferId={}, mapCfgId={}, groupId={}",
                playerId, transferId, mapCfgId, groupId);
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
                ClientFrameChunk.wrap(MsgId.S2C_READY_ENTER_MAP_VALUE, message));
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
