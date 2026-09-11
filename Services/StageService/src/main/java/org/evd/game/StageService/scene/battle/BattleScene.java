package org.evd.game.StageService.scene.battle;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.StageService;
import org.evd.game.common.proto.MapMsgId;
import org.evd.game.common.proto.S2C_EnterMap;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.PlayerService.PlayerMapRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRobot;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.StageService.scene.movable.BattleRole;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/** Stage 上所有具体场景的父类，负责预进入队列和正式玩家集合。 */
@Slf4j
public class BattleScene {
    private final SMapKey mapKey;
    private final long sceneId;
    private final StageService owner;
    private final Map<Long, PlayerEnterRequest> pendingRoleMap = new HashMap<>();
    private final Map<Long, BattleRole> roleMap = new HashMap<>();

    public BattleScene(SMapKey mapKey, long sceneId, StageService owner) {
        this.mapKey = new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
        this.sceneId = sceneId;
        this.owner = owner;
    }

    public boolean canEnter(PlayerEnterRequest request) {
        if (request == null) {
            log.warn("BattleScene 拒绝进入：请求为空, sceneId={}", sceneId);
            return false;
        }
        long playerId = request.getPlayerId();
        if (playerId <= 0L) {
            log.warn("BattleScene 拒绝进入：playerId 无效, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        SMapInfo targetInfo = request.getTargetInfo();
        if (targetInfo == null) {
            log.warn("BattleScene 拒绝进入：目标地图信息为空, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        if (sceneId != targetInfo.getSceneId()) {
            log.warn("BattleScene 拒绝进入：sceneId 不匹配, playerId={}, sceneId={}, targetSceneId={}",
                    playerId, sceneId, targetInfo.getSceneId());
            return false;
        }
        if (!mapKey.equals(targetInfo.toMapKey())) {
            log.warn("BattleScene 拒绝进入：地图标识不匹配, playerId={}, sceneId={}, mapCfgId={}, groupId={}, targetMapCfgId={}, targetGroupId={}",
                    playerId, sceneId, mapKey.getMapCfgId(), mapKey.getGroupId(),
                    targetInfo.getMapCfgId(), targetInfo.getGroupId());
            return false;
        }
        if (roleMap.containsKey(playerId)) {
            log.info("BattleScene 拒绝进入：玩家已在场景中, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        if (pendingRoleMap.containsKey(playerId)) {
            log.info("BattleScene 拒绝进入：玩家已在预进入队列中, playerId={}, sceneId={}", playerId, sceneId);
            return false;
        }
        return true;
    }

    public void addPendingRole(PlayerEnterRequest request) {
        pendingRoleMap.put(request.getPlayerId(), request);
    }

    public final void roleEnter(SPlayerMapData playerData) {
        if (playerData == null || playerData.getPlayerId() <= 0L) {
            log.error("BattleScene 玩家正式进入场景失败：玩家数据为空或 playerId 无效, playerId={}, sceneId={}",
                    playerData == null ? null : playerData.getPlayerId(), sceneId);
            return;
        }
        if (playerData.getGateActorAddress() == null || playerData.getPlayerActorAddress() == null) {
            log.warn("BattleScene 拒绝进入：玩家地址为空, playerId={}, sceneId={}",
                    playerData.getPlayerId(), sceneId);
            return;
        }
        long playerId = playerData.getPlayerId();
        if (roleMap.containsKey(playerId)) {
            log.info("BattleScene 拒绝重复进入：玩家已经在场景中, playerId={}, sceneId={}",
                    playerId, sceneId);
            return;
        }
        PlayerEnterRequest request = pendingRoleMap.get(playerId);
        if (request == null) {
            log.warn("BattleScene 拒绝进入：玩家不在预进入队列中, playerId={}, sceneId={}",
                    playerId, sceneId);
            return;
        }
        PlayerEnterRequest enterRequest = pendingRoleMap.remove(playerId);
        BattleRole battleRole = new BattleRole(playerData);
        roleMap.put(playerId, battleRole);
        owner.getMessageLocationSender().cache(ActorId.gate(playerId), playerData.getGateActorAddress());
        owner.getMessageLocationSender().cache(ActorId.player(playerId), playerData.getPlayerActorAddress());
        ActorAddress stageActorAddress = owner.registerMapPlayerActor(playerId);
        log.info("BattleScene 玩家正式进入场景: playerId={}, sceneId={}, stageActorAddress={}",
                playerId, sceneId, stageActorAddress);
        RpcResult<Boolean> notifyResult = PlayerMapRpcProxy.callOnEnterMap(
                request.getPlayerService(), playerId, request.getTransferId(),
                request.getTargetInfo(), stageActorAddress);
        if (!notifyResult.isSuccess() || !Boolean.TRUE.equals(notifyResult.getValue())) {
            log.error("BattleScene 通知 PlayerService 玩家进入地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, notifyResult.getErrorCode(), notifyResult.getErrorMessage());
            return;
        }
        S2C_EnterMap message = S2C_EnterMap.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
                .setSceneId(sceneId)
                .setMapCfgId(mapKey.getMapCfgId())
                .setGroupId(mapKey.getGroupId())
                .build();
        RpcResult<Void> enterMapResult = ConnServiceRpcProxy.callPushToPlayerId(
                playerId, ClientFrameChunk.wrap(MapMsgId.S2C_MAP_ENTER_MAP_VALUE, message));
        if (!enterMapResult.isSuccess()) {
            log.error("BattleScene 通知客户端正式进入地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, enterMapResult.getErrorCode(), enterMapResult.getErrorMessage());
            return;
        }
        if (!roleMap.containsKey(playerId)) {
            log.warn("BattleScene RPC 调用结束后玩家已不在场景中: playerId={}, sceneId={}",
                    playerId, sceneId);
            return;
        }
        try {
            onRoleEnter(battleRole);
        } catch (Exception e) {
            log.error("BattleScene 玩家进入场景回调失败: playerId={}, sceneId={}",
                    playerId, sceneId, e);
        }
    }

    public void cancelPending(long playerId) {
        pendingRoleMap.remove(playerId);
    }

    public final boolean roleExit(long playerId) {
        if (playerId <= 0L) {
            return false;
        }
        PlayerEnterRequest request = pendingRoleMap.get(playerId);
        BattleRole battleRole = roleMap.get(playerId);
        if (battleRole == null && request == null) {
            log.warn("BattleScene 玩家退出失败，找不到玩家状态: playerId={}, sceneId={}", playerId, sceneId);
            return true;
        }
        if (battleRole == null) {
            pendingRoleMap.remove(playerId);
            log.info("BattleScene 玩家退出预进入队列: playerId={}, sceneId={}", playerId, sceneId);
            return true;
        }
        CallPoint playerService = battleRole.getPlayerMapData().getPlayerActorAddress().getCallPoint();
        pendingRoleMap.remove(playerId);
        roleMap.remove(playerId);
        ActorAddress stageActorAddress = owner.unregisterMapPlayerActor(playerId);
        owner.getMessageLocationSender().remove(ActorId.gate(playerId));
        owner.getMessageLocationSender().remove(ActorId.player(playerId));

        if (playerService == null) {
            log.info("BattleScene 玩家退出场景完成: playerId={}, sceneId={}, stageActorAddress={}",
                    playerId, sceneId, stageActorAddress);
            return true;
        }
        RpcResult<Void> exitResult = PlayerMapRpcProxy.sendOnExitMap(
                playerService, playerId, sceneId, stageActorAddress);
        if (!exitResult.isSuccess()) {
            log.warn("StageService 通知 PlayerService 玩家退出地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, exitResult.getErrorCode(), exitResult.getErrorMessage());
        }
        log.info("BattleScene 玩家退出场景完成: playerId={}, sceneId={}, stageActorAddress={}",
                playerId, sceneId, stageActorAddress);
        try {
            onRoleExit(battleRole);
        } catch (Exception e) {
            log.error("BattleScene 玩家退出场景回调失败: playerId={}, sceneId={}",
                    playerId, sceneId, e);
        }
        return true;
    }

    protected void onRoleEnter(BattleRole battleRole) {
    }

    protected void onRoleExit(BattleRole battleRole) {
    }

    public boolean isEmpty() {
        return pendingRoleMap.isEmpty() && roleMap.isEmpty();
    }

    public SRunningMapInfo getRunningMapInfo() {
        int hostRoleNum = 0;
        int guestRoleNum = 0;
        for (BattleRole role : roleMap.values()) {
            int camp = getPlayerCamp(role.getPlayerId());
            if (camp == 1) hostRoleNum++;
            if (camp == 2) guestRoleNum++;
        }
        for (PlayerEnterRequest request : pendingRoleMap.values()) {
            int camp = getPlayerCamp(request.getPlayerId());
            if (camp == 1) hostRoleNum++;
            if (camp == 2) guestRoleNum++;
        }
        var robots = getSceneRobots();
        for (var robot : robots) {
                if (robot.getCamp() == 1) hostRoleNum++;
                if (robot.getCamp() == 2) guestRoleNum++;
        }
        var mapConfig = MapConfigs.get(mapKey.getMapCfgId());
        int sideLimit = mapConfig == null ? 0 : mapConfig.getPlayerLimit() / 2;
        return new SRunningMapInfo(new SMapInfo(sceneId, mapKey.getMapCfgId(), mapKey.getGroupId()),
                hostRoleNum, guestRoleNum, sideLimit);
    }

    public SMapKey getMapKey() {
        return new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
    }

    protected int getPlayerCamp(long playerId) {
        return 0;
    }

    protected List<SMatchRobot> getSceneRobots() {
        return List.of();
    }
}
