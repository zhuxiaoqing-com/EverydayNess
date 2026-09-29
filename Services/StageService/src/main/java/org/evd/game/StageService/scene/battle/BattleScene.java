package org.evd.game.StageService.scene.battle;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.StageService;
import org.evd.game.common.constant.MapConst;
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
import org.evd.game.common.config.table.MapConfig;
import org.evd.game.StageService.scene.movable.BattleRole;
import org.evd.game.StageService.scene.movable.Monster;
import org.evd.game.StageService.scene.logic.BuffLogic;
import org.evd.game.StageService.scene.logic.MonsterLogic;
import org.evd.game.StageService.scene.logic.SkillLogic;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage 上所有具体场景的父类，负责预进入队列和正式玩家集合。 */
@Slf4j
public class BattleScene {
    private static final long MIN_WAIT_TIME_MILLIS = 5_000L;

    public enum State {
        CREATED,
        WAIT,
        FIGHTING,
        FINISH,
        DESTROYED
    }

    private final SMapKey mapKey;
    private final long sceneId;
    private final StageService owner;
    private final Map<Long, PlayerEnterRequest> pendingRoleMap = new HashMap<>();
    private final Map<Long, BattleRole> roleMap = new HashMap<>();
    private final Map<Long, Monster> monsterMap = new HashMap<>();
    private long nextMonsterId = 1L;
    private final BuffLogic buffLogic;
    private final SkillLogic skillLogic;
    private final long waitDuration;
    private final long countTime;
    private final long validTime;
    private final long destroyDelay;
    protected State state = State.CREATED;
    protected long createTime = -1L;
    protected long waitTime = -1L;
    protected long startTime = -1L;
    protected long summaryTime = -1L;
    protected long destroyTime = -1L;
    protected long realSummaryTime = -1L;
    private boolean finish;
    private boolean timeOut;
    private long lastSecondHeartTime;

    public BattleScene(SMapKey mapKey, long sceneId, StageService owner) {
        this.mapKey = new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
        this.sceneId = sceneId;
        this.owner = owner;
        this.buffLogic = owner.getActor(BuffLogic.class);
        this.skillLogic = owner.getActor(SkillLogic.class);
        MapConfig mapConfig = MapConfigs.get(mapKey.getMapCfgId());
        this.waitDuration = mapConfig.getWaitTime() * 1000L;
        this.countTime = mapConfig.getCountTime() * 1000L;
        this.validTime = mapConfig.getValidTime() * 1000L;
        this.destroyDelay = mapConfig.getDestroyDelay() * 1000L;
        initBattleState();
        MonsterLogic monsterLogic = owner.getActor(MonsterLogic.class);
        monsterLogic.loadConfiguredMonsters(this, mapKey.getMapCfgId());
    }

    private void initBattleState() {
        createTime = System.currentTimeMillis();
        state = State.CREATED;
        waitTime = createTime + Math.max(waitDuration, MIN_WAIT_TIME_MILLIS);
    }

    public long allocateMonsterId() { return nextMonsterId++; }

    public void addMonster(Monster monster) { monsterMap.put(monster.getId(), monster); }

    public BattleUnit findUnit(long unitId) {
        BattleRole role = roleMap.get(unitId);
        return role != null ? role : monsterMap.get(unitId);
    }

    public void tick(long now) {
        if (state == State.DESTROYED) {
            return;
        }
        roleMap.values().forEach(role -> role.update(now));
        monsterMap.values().forEach(monster -> monster.update(now));
        monsterMap.values().removeIf(monster -> !monster.isAlive());
        updateState(now);
    }

    protected void updateState(long now) {
        switch (state) {
            case CREATED:
                if (checkWait(now)) {
                    state = State.WAIT;
                    onWait(now);
                }
                break;
            case WAIT:
                if (checkFighting(now)) {
                    state = State.FIGHTING;
                    onFighting(now);
                }
                break;
            case FIGHTING:
                heart(now);
                if (checkFinish(now)) {
                    state = State.FINISH;
                    onFinish(now);
                }
                break;
            case FINISH:
                if (now > destroyTime || (roleMap.isEmpty() && pendingRoleMap.isEmpty())) {
                    state = State.DESTROYED;
                }
                break;
            case DESTROYED:
                break;
        }
    }

    protected boolean checkWait(long now) {
        return now >= waitTime;
    }

    protected void onWait(long now) {
        startTime = now + countTime;
    }

    protected boolean checkFighting(long now) {
        return now >= startTime;
    }

    protected void onFighting(long now) {
        long configuredSummaryTime = findSummaryTime(now);
        if (configuredSummaryTime > 0) {
            summaryTime = now + configuredSummaryTime;
        }
    }

    protected long findSummaryTime(long now) {
        return validTime;
    }

    protected boolean checkFinish(long now) {
        if (finish) {
            return true;
        }
        if (summaryTime > 0 && now > summaryTime) {
            timeOut = true;
            return true;
        }
        return false;
    }

    protected void onFinish(long now) {
        realSummaryTime = now;
        destroyTime = now + destroyDelay;
        if (!finish) {
            timeOut();
            setFinish(true);
        }
        summary();
    }

    public final void heart(long now) {
        try {
            _heart(now);
        } catch (Exception e) {
            log.error("BattleScene 战场心跳异常: sceneId={}", sceneId, e);
        }
    }

    protected void _heart(long now) {
        if (now - lastSecondHeartTime >= 1000L) {
            lastSecondHeartTime = now;
            secondHeart(now);
        }
    }

    protected void secondHeart(long now) {
    }

    protected void summary() {
    }

    protected void timeOut() {
        timeOut = true;
    }

    public State getState() {
        return state;
    }

    public void markDestroyed() {
        state = State.DESTROYED;
    }

    public boolean isFightState() {
        return state == State.FIGHTING;
    }

    public void setFinish(boolean finish) {
        this.finish = finish;
    }

    public boolean isFinish() {
        return finish;
    }

    public boolean isTimeOut() {
        return timeOut;
    }

    public boolean canEnter(PlayerEnterRequest request) {
        if (state == State.FINISH || state == State.DESTROYED) {
            log.info("BattleScene 拒绝进入已结束场景: playerId={}, sceneId={}, state={}",
                    request == null ? 0L : request.getPlayerId(), sceneId, state);
            return false;
        }
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
        MapConfig mapConfig = MapConfigs.get(mapKey.getMapCfgId());
        if (mapConfig != null && mapConfig.getPlayerLimit() > 0
                && roleMap.size() + pendingRoleMap.size() + getSceneRobots().size()
                >= mapConfig.getPlayerLimit()) {
            log.info("BattleScene 拒绝进入：地图人数已满, playerId={}, sceneId={}, limit={}",
                    playerId, sceneId, mapConfig.getPlayerLimit());
            return false;
        }
        return true;
    }

    public void addPendingRole(PlayerEnterRequest request) {
        pendingRoleMap.put(request.getPlayerId(), request);
    }

    public boolean hasPendingRole(PlayerEnterRequest request) {
        return pendingRoleMap.get(request.getPlayerId()) == request;
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
        pendingRoleMap.remove(playerId);
        BattleRole battleRole = new BattleRole(playerData, buffLogic, skillLogic);
        roleMap.put(playerId, battleRole);
        owner.getMessageLocationSender().cache(ActorId.gate(playerId), playerData.getGateActorAddress());
        owner.getMessageLocationSender().cache(ActorId.player(playerId), playerData.getPlayerActorAddress());
        ActorAddress stageActorAddress = owner.registerMapPlayerActor(playerId);
        log.info("BattleScene 玩家正式进入场景: playerId={}, sceneId={}, stageActorAddress={}",
                playerId, sceneId, stageActorAddress);
        RpcResult<Boolean> notifyResult = PlayerMapRpcProxy.callOnEnterMap(
                request.getPlayerService(), playerId, request.getTransferId(),
                request.getTargetInfo(), stageActorAddress);
        if (roleMap.get(playerId) != battleRole) {
            log.warn("BattleScene 通知 PlayerService 进入地图返回后玩家已被移除，放弃后续进入处理: playerId={}, sceneId={}",
                    playerId, sceneId);
            return;
        }
        if (!notifyResult.isSuccess() || !Boolean.TRUE.equals(notifyResult.getValue())) {
            log.error("BattleScene 通知 PlayerService 玩家进入地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, notifyResult.getErrorCode(), notifyResult.getErrorMessage());
            roleExit(playerId, MapConst.RoleExitType.ENTER_FAILED);
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
        if (roleMap.get(playerId) != battleRole) {
            log.warn("BattleScene 通知客户端进入地图返回后玩家已被移除，放弃后续进入处理: playerId={}, sceneId={}",
                    playerId, sceneId);
            return;
        }
        if (!enterMapResult.isSuccess()) {
            log.error("BattleScene 通知客户端正式进入地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    playerId, sceneId, enterMapResult.getErrorCode(), enterMapResult.getErrorMessage());
            roleExit(playerId, MapConst.RoleExitType.ENTER_FAILED);
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

    public void cancelPending(PlayerEnterRequest request) {
        pendingRoleMap.remove(request.getPlayerId(), request);
    }

    /** 对端失联只移除它的玩家，场景仍由当前 Stage 持有。 */
    public void onPlayerServiceDisconnect(CallPoint playerService) {
        removePlayersByPlayerServiceDisconnect(playerService);
    }

    private void removePlayersByPlayerServiceDisconnect(CallPoint playerService) {
        int pendingBefore = pendingRoleMap.size();
        int rolesBefore = roleMap.size();
        // roleExit 会移除角色，且可能触发业务回调，因此先收集本轮受影响的玩家。
        List<Long> playerIds = new ArrayList<>();
        pendingRoleMap.values().stream()
                .filter(request -> playerService.equals(request.getPlayerService()))
                .map(PlayerEnterRequest::getPlayerId)
                .forEach(playerIds::add);
        roleMap.values().stream().filter(role ->
            role.getPlayerMapData().getPlayerActorAddress() != null
                    && playerService.equals(role.getPlayerMapData().getPlayerActorAddress().getCallPoint()))
                .map(BattleRole::getPlayerId)
                .forEach(playerIds::add);
        for (long playerId : playerIds) {
            roleExit(playerId, MapConst.RoleExitType.PLAYER_SERVICE_DISCONNECT);
        }
        int pendingRemoved = pendingBefore - pendingRoleMap.size();
        int rolesRemoved = rolesBefore - roleMap.size();
        if (pendingRemoved > 0 || rolesRemoved > 0) {
            log.info("StageService 场景完成服务断开清理: sceneId={}, serviceType={}, disconnectedService={}, exitType={}, pendingRemoved={}, rolesRemoved={}",
                    sceneId, "PLAYER", playerService,
                    MapConst.RoleExitType.name(MapConst.RoleExitType.PLAYER_SERVICE_DISCONNECT),
                    pendingRemoved, rolesRemoved);
        }
    }

    public Map<Long, BattleRole> getRoleMap() {
        return roleMap;
    }

    public final boolean roleExit(long playerId) {
        return roleExit(playerId, MapConst.RoleExitType.NORMAL);
    }

    public final boolean roleExit(long playerId, int exitType) {
        if (playerId <= 0L) {
            return false;
        }
        PlayerEnterRequest request = pendingRoleMap.get(playerId);
        BattleRole battleRole = roleMap.get(playerId);
        if (battleRole == null && request == null) {
            log.warn("BattleScene 玩家退出失败，找不到玩家状态: playerId={}, sceneId={}, exitType={}",
                    playerId, sceneId, MapConst.RoleExitType.name(exitType));
            return true;
        }
        if (battleRole == null) {
            pendingRoleMap.remove(playerId);
            log.info("BattleScene 玩家退出预进入队列: playerId={}, sceneId={}, exitType={}",
                    playerId, sceneId, MapConst.RoleExitType.name(exitType));
            return true;
        }
        CallPoint playerService = battleRole.getPlayerMapData().getPlayerActorAddress().getCallPoint();
        pendingRoleMap.remove(playerId);
        roleMap.remove(playerId);
        ActorAddress stageActorAddress = owner.unregisterMapPlayerActor(playerId);
        owner.getMessageLocationSender().remove(ActorId.gate(playerId));
        owner.getMessageLocationSender().remove(ActorId.player(playerId));

        // 这个在 onRoleExit 前面调用;防止onRoleExit里有call函数;
        boolean notifyPlayerService = exitType != MapConst.RoleExitType.PLAYER_SERVICE_DISCONNECT;
        if (playerService != null && notifyPlayerService) {
            RpcResult<Void> exitResult = PlayerMapRpcProxy.sendOnExitMap(
                    playerService, playerId,
                    new SMapInfo(sceneId, mapKey.getMapCfgId(), mapKey.getGroupId()), exitType);
            if (!exitResult.isSuccess()) {
                log.warn("StageService 通知 PlayerService 玩家退出地图失败: playerId={}, sceneId={}, exitType={}, errorCode={}, message={}",
                        playerId, sceneId, MapConst.RoleExitType.name(exitType),
                        exitResult.getErrorCode(), exitResult.getErrorMessage());
            }
        }
        log.info("BattleScene 玩家退出场景完成: playerId={}, sceneId={}, stageActorAddress={}, exitType={}, notifyPlayerService={}",
                playerId, sceneId, stageActorAddress, MapConst.RoleExitType.name(exitType), notifyPlayerService);

        try {
            onRoleExit(battleRole);
        } catch (Exception e) {
            log.error("BattleScene 玩家退出场景回调失败: playerId={}, sceneId={}, exitType={}",
                    playerId, sceneId, MapConst.RoleExitType.name(exitType), e);
        }
        return true;
    }

    protected void onRoleEnter(BattleRole battleRole) {
    }

    protected void onRoleExit(BattleRole battleRole) {
    }

    public void onMapDestroyed() {
        for (long playerId : new ArrayList<>(roleMap.keySet())) {
            roleExit(playerId, MapConst.RoleExitType.MAP_DESTROYED);
        }
        for (PlayerEnterRequest request : new ArrayList<>(pendingRoleMap.values())) {
            if (!pendingRoleMap.remove(request.getPlayerId(), request)) {
                continue;
            }
            CallPoint playerService = request.getPlayerService();
            if (playerService == null) {
                log.warn("BattleScene 销毁时待进入玩家缺少 PlayerService 地址: playerId={}, sceneId={}",
                        request.getPlayerId(), sceneId);
                continue;
            }
            RpcResult<Void> exitResult = PlayerMapRpcProxy.sendOnExitMap(
                    playerService, request.getPlayerId(),
                    new SMapInfo(sceneId, mapKey.getMapCfgId(), mapKey.getGroupId()),
                    MapConst.RoleExitType.MAP_DESTROYED);
            if (!exitResult.isSuccess()) {
                log.warn("StageService 通知待进入玩家退出销毁地图失败: playerId={}, sceneId={}, exitType={}, errorCode={}, message={}",
                        request.getPlayerId(), sceneId,
                        MapConst.RoleExitType.name(MapConst.RoleExitType.MAP_DESTROYED),
                        exitResult.getErrorCode(), exitResult.getErrorMessage());
            }
        }

        monsterMap.clear();
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

    public long getSceneId() {
        return sceneId;
    }

    protected int getPlayerCamp(long playerId) {
        return 0;
    }

    protected List<SMatchRobot> getSceneRobots() {
        return List.of();
    }
}
