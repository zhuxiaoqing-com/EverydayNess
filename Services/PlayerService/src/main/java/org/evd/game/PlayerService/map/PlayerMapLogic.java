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
import org.evd.game.common.proxy.SceneManagerService.SceneManagerRpcProxy;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;
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

    /** 登录进入默认地图；已有转场上下文按登录规则等待后清理。 */
    public void enterMapOnLogin(long playerId) {
        startTransfer(playerId, LOGIN_MAP_CFG_ID, LOGIN_GROUP_ID, true);
    }

    /** 发起一次普通地图转场。 */
    public void enterMap(long playerId, int mapCfgId, long groupId) {
        startTransfer(playerId, mapCfgId, groupId, false);
    }

    private void startTransfer(long playerId, int mapCfgId, long groupId, boolean login) {
        PlayerService owner = owner();
        if(!owner.sessionManager().hasOnlinePlayer(playerId)) {
            log.warn("PlayerService 玩家不在线，忽略地图进入请求: playerId={}", playerId);
            return;
        }
        DBRoleMapData roleMapData = getOrCreateRoleMapData(playerId);
        DBTransferContext oldContext = roleMapData.getTransferContext();
        if (oldContext.getStartMill() > 0L) {
            if (!login) {
                log.warn("PlayerService 玩家已有地图转场，忽略新的进入请求: playerId={}, startMill={}",
                        playerId, oldContext.getStartMill());
                return;
            }
            long oldStartMill = oldContext.getStartMill();
            Service.getCurrent().sleep(2_000L);

            if (!owner.sessionManager().hasOnlinePlayer(playerId)) {
                log.warn("PlayerService 玩家不在线，等待2秒，忽略地图进入请求: playerId={}", playerId);
                return;
            }

            // 不一样就是有新的进入场景进来了
            if (oldContext.getStartMill() == oldStartMill) {
                roleMapData.setTransferContext(new DBTransferContext());
            }

            log.error("PlayerService 登录时发现旧地图转场，等待后强制清空: playerId={}, oldStartMill {} startMill={}",
                    playerId, oldStartMill, oldContext.getStartMill());
        }

        long transferId = Service.getTime();
        SMapInfo oldMapInfo = toCommon(roleMapData.getCurrMapInfo());
        SMapInfo targetInfo = new SMapInfo(0L, mapCfgId, groupId);
        DBTransferContext transferContext = new DBTransferContext();
        transferContext.setStartMill(transferId);
        transferContext.setStart(false);
        transferContext.setOldMapInfo(toDb(oldMapInfo));
        transferContext.setTargetInfo(toDb(targetInfo));
        roleMapData.setTransferContext(transferContext);

        SMapEnterRequest request = new SMapEnterRequest(playerId, transferId, owner.getCallPoint(),
                oldMapInfo, targetInfo);
        CallPoint sceneManager = MapConst.getSceneManagerCallPoint(mapCfgId);
        RpcResult<Boolean> result = SceneManagerRpcProxy.callEnterMap(sceneManager, request);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.error("PlayerService 发送地图转场请求失败: playerId={}, transferId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                    playerId, transferId, mapCfgId, groupId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    /** Stage 完成旧场景退出后调用，通知客户端开始加载目标地图。 */
    public boolean readyEnterMap(long playerId, long transferId, SMapInfo targetInfo) {
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBTransferContext context = roleMapData == null ? null : roleMapData.getTransferContext();
        if (context == null || context.getStartMill() <= 0L || context.getStartMill() != transferId
                || targetInfo == null) {
            log.warn("PlayerService 地图转场上下文已失效，忽略 ReadyEnterMap: playerId={}, transferId={}",
                    playerId, transferId);
            return false;
        }
        context.setStart(true);
        context.setTargetInfo(toDb(targetInfo));

        PPlayerOnline online = owner().sessionManager().get(playerId);
        if (online == null || online.getGate() == null || online.getGateSessionId() <= 0L) {
            log.warn("PlayerService 玩家会话不存在，无法通知客户端加载地图: playerId={}, transferId={}",
                    playerId, transferId);
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
        RpcResult<Boolean> result = ConnServiceRpcProxy.callPushToClient(online.getGate(), online.getGateSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_READY_ENTER_MAP_VALUE, message));
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.warn("PlayerService 通知客户端加载地图失败: playerId={}, transferId={}, errorCode={}, message={}",
                    playerId, transferId, result.getErrorCode(), result.getErrorMessage());
            return false;
        }
        return true;
    }

    /** Stage 完成退出后通知 PlayerService 清理当前地图。 */
    public void onExitMap(long playerId, long sceneId) {
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBMapInfo current = roleMapData == null ? null : roleMapData.getCurrMapInfo();
        if (current == null || current.getSceneId() != sceneId) {
            log.warn("PlayerService 收到玩家退出地图通知，但当前地图不匹配: playerId={}, sceneId={}, current={}",
                    playerId, sceneId, current);
            return;
        }
        roleMapData.setCurrMapInfo(new DBMapInfo());
    }

    /** 客户端加载完成后，调用 Stage 让 SceneBattle 正式接纳玩家。 */
    public void confirmReadyEnterMap(ClientSessionRef session, C2S_ReadyEnterMap request) {
        long playerId = session.getPlayerId();
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBTransferContext context = roleMapData == null ? null : roleMapData.getTransferContext();
        if (context == null || context.getStartMill() <= 0L || !context.getStart()
                || context.getStartMill() != request.getTransferId()
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
            return;
        }
        RpcResult<Boolean> result = StageServiceRpcProxy.callEnterScene(stage.getValue(),
                targetInfo.getSceneId(), playerId, request.getTransferId());
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getValue())) {
            log.error("PlayerService Stage 正式进入地图失败: playerId={}, transferId={}, sceneId={}, errorCode={}, message={}",
                    playerId, request.getTransferId(), targetInfo.getSceneId(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }
        roleMapData.setCurrMapInfo(new DBMapInfo(targetInfo));
        roleMapData.setTransferContext(new DBTransferContext());
        if (!owner().sessionManager().markOnline(playerId)) {
            log.warn("PlayerService 玩家已进入地图但在线状态推进失败: playerId={}, transferId={}",
                    playerId, request.getTransferId());
        }
        log.info("PlayerService 玩家正式进入地图: playerId={}, transferId={}, sceneId={}, mapCfgId={}, groupId={}",
                playerId, request.getTransferId(), targetInfo.getSceneId(), targetInfo.getMapCfgId(), targetInfo.getGroupId());
    }

    /** 玩家离线时从当前场景移除。 */
    public void leaveMap(long playerId) {
        DBRoleMapData roleMapData = DBRoleMapDataTable.get(playerId);
        DBTransferContext transferContext = roleMapData == null ? null : roleMapData.getTransferContext();
        if (transferContext != null && transferContext.getStartMill() > 0L
                && transferContext.getTargetInfo().getSceneId() > 0L) {
            removeFromScene(playerId, transferContext.getTargetInfo());
        }
        DBMapInfo current =
                roleMapData == null ? null : roleMapData.getCurrMapInfo();
        if (current == null || current.getSceneId() <= 0L) {
            return;
        }
        removeFromScene(playerId, current);
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
}
