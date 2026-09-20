package org.evd.game.OnlineService.session;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.OnlineService.session.SOnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.CoroutineLockTimeoutException;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** OnlineService 在线会话与玩家服务绑定业务。 */
@Actor
public final class OnlineSessionLogic {
    private final UserIdPlayerServiceMap historicalPlayerServiceMap = new UserIdPlayerServiceMap();
    private final OnlinePlayerRegistry onlinePlayerRegistry = new OnlinePlayerRegistry();
    private final Map<String, SOnlineUserState> userStates = new HashMap<>();

    /** 查询用户正式上线后的会话状态。 */
    public SOnlineUserState getUserState(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userStates.get(userId);
    }

    /** 判断用户当前会话是否仍对应指定网关和会话号。 */
    public boolean matchesSession(String userId, CallPoint gate, long gateSessionId) {
        return !isSessionMismatch(userStates.get(userId), gate, gateSessionId);
    }

    /** 判断用户当前是否不存在正式在线会话。 */
    public boolean isPlayerOffline(String userId) {
        return userId == null || userId.isBlank() || !userStates.containsKey(userId);
    }

    /** 判断用户是否已经创建正式在线状态。 */
    public boolean hasUserState(String userId) {
        return userId != null && !userId.isBlank() && userStates.containsKey(userId);
    }

    /** 返回当前正式在线用户数量。 */
    public int userStateCount() {
        return userStates.size();
    }

    /** 返回用户当前登记的在线玩家，包括尚未完成 PlayerService RPC 的上线流程。 */
    public OnlinePlayer getOnlinePlayer(String userId) {
        SOnlineUserState userState = userStates.get(userId);
        if (userState == null || userState.getActivePlayerId() <= 0L) {
            return null;
        }
        OnlinePlayer onlinePlayer = onlinePlayerRegistry.get(userState.getActivePlayerId());
        return onlinePlayer != null && userId.equals(onlinePlayer.getUserId()) ? onlinePlayer : null;
    }

    /** 返回当前在线状态的只读集合视图。 */
    public Collection<SOnlineUserState> getUserStates() {
        return Collections.unmodifiableCollection(userStates.values());
    }

    /** 返回用户历史绑定的 PlayerService，用于同一玩家优先回到原服务。 */
    public CallPoint getHistoricalPlayerService(String userId) {
        return historicalPlayerServiceMap.get(userId);
    }

    /** 接收服务断开事件，设置断开的 PlayerService 历史绑定过期时间。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        historicalPlayerServiceMap.onServiceDisconnect(serviceList);
    }

    /** 周期清理已经超过保留时间的 PlayerService 历史绑定。 */
    public void tick(long currentTime) {
        historicalPlayerServiceMap.expire(currentTime);
    }

    /** 创建新正式会话；网关 actor 和 Location 等选定 playerId 后再注册。 */
    public void createOnlineState(String userId, CallPoint gate, long gateSessionId) {
        SOnlineUserState newState = new SOnlineUserState(
                userId, gate, gateSessionId, 0L, null, null, null);
        userStates.put(userId, newState);
    }

    /** 在调用 PlayerService 前登记 OnlinePlayer，断线时可直接回滚该上线流程。 */
    public OnlinePlayer beginOnlinePlayer(String userId, CallPoint gate, long gateSessionId,
                                          long playerId, CallPoint playerService) {
        SOnlineUserState userState = userStates.get(userId);
        if (isSessionMismatch(userState, gate, gateSessionId)) {
            LogCore.core.warn("OnlineService 登记 OnlinePlayer 失败，Session 已失效: userId={}, playerId={}, gate={}, gateSessionId={}, playerService={}, currentState={}",
                    userId, playerId, gate, gateSessionId, playerService, userState);
            return null;
        }
        if (getOnlinePlayer(userId) != null) {
            return null;
        }
        OnlinePlayer onlinePlayer = onlinePlayerRegistry.begin(userId, playerId);
        if (onlinePlayer != null) {
            userState.setActivePlayerId(playerId);
            userState.setActivePlayerService(playerService);
            historicalPlayerServiceMap.bind(onlinePlayer.getUserId(), playerService);
        }
        return onlinePlayer;
    }

    /** 将 PlayerService 返回的玩家 ActorAddress 登记到当前在线状态。 */
    public boolean bindPlayerActorAddress(OnlinePlayer onlinePlayer, ActorAddress actorAddress) {
        if (actorAddress == null || !onlinePlayerRegistry.isCurrent(onlinePlayer)) {
            return false;
        }
        SOnlineUserState userState = userStates.get(onlinePlayer.getUserId());
        if (userState == null || getOnlinePlayer(onlinePlayer.getUserId()) != onlinePlayer) {
            return false;
        }
        onlinePlayer.markPlayerReady();
        userState.setActivePlayerActorAddress(actorAddress);
        owner().getMessageLocationSender().cache(
                ActorId.player(onlinePlayer.getPlayerId()), actorAddress);
        LogCore.core.info("OnlineService 缓存 PlayerActorAddress: userId={}, playerId={}, actorId={}, actorAddress={}",
                onlinePlayer.getUserId(), onlinePlayer.getPlayerId(),
                ActorId.player(onlinePlayer.getPlayerId()), actorAddress);
        return true;
    }

    /** 删除仍指向指定 PlayerService 的历史绑定。 */
    public void removeHistoricalPlayerService(String userId, CallPoint expectedPlayerService) {
        if (!historicalPlayerServiceMap.remove(userId, expectedPlayerService)) {
            LogCore.core.info("OnlineService 历史 PlayerService 绑定已变化，跳过过期回调删除: userId={}, playerService={}",
                    userId, expectedPlayerService);
        }
    }

    /** 接收 PlayerService 当前 MDB 中仍保留的玩家，覆盖该服务的历史绑定。 */
    public void restoreHistoricalPlayerServices(Collection<String> userIds, CallPoint playerService) {
        int restored = historicalPlayerServiceMap.bindAll(userIds, playerService);
        LogCore.core.info("OnlineService 恢复 PlayerService 历史绑定: playerService={}, requested={}, restored={}",
                playerService, userIds == null ? 0 : userIds.size(), restored);
    }

    /** 将 GW 返回的玩家 ActorAddress 登记到当前在线状态。 */
    public void bindGateActorAddress(OnlinePlayer onlinePlayer, ActorAddress actorAddress) {
        SOnlineUserState userState = userStates.get(onlinePlayer.getUserId());
        onlinePlayer.markGateBound();
        userState.setActiveGateActorAddress(actorAddress);
        owner().getMessageLocationSender().cache(
                ActorId.gate(onlinePlayer.getPlayerId()), actorAddress);
        LogCore.core.info("OnlineService 缓存 GWActorAddress: userId={}, playerId={}, actorId={}, actorAddress={}",
                onlinePlayer.getUserId(), onlinePlayer.getPlayerId(),
                ActorId.gate(onlinePlayer.getPlayerId()), actorAddress);
    }

    /** 仅在网关会话匹配时清理在线状态，并返回已绑定的 PlayerService。 */
    public CallPoint clearSession(String userId, CallPoint gate, long gateSessionId) {
        SOnlineUserState userState = userStates.get(userId);
        if (isSessionMismatch(userState, gate, gateSessionId)) {
            return null;
        }
        CallPoint playerService = userState.getActivePlayerService();
        onlinePlayerRegistry.remove(userState.getActivePlayerId());
        userStates.remove(userId, userState);
        return playerService;
    }

    /** 校验会话及预期服务后解除 PlayerService 绑定。 */
    public boolean clearPlayerService(String userId, CallPoint gate, long gateSessionId,
                                      CallPoint expectedPlayerService) {
        SOnlineUserState userState = userStates.get(userId);
        if (gate == null || gateSessionId <= 0L || expectedPlayerService == null
                || isSessionMismatch(userState, gate, gateSessionId)
                || !expectedPlayerService.equals(userState.getActivePlayerService())) {
            return false;
        }
        OnlinePlayer onlinePlayer = onlinePlayerRegistry.get(userState.getActivePlayerId());
        if (onlinePlayer != null
                && onlinePlayerRegistry.remove(userState.getActivePlayerId()) == null) {
            return false;
        }
        removeActorAddresses(userState.getActivePlayerId());
        userState.setActivePlayerService(null);
        userState.setActivePlayerActorAddress(null);
        userState.setActiveGateActorAddress(null);
        userState.setActivePlayerId(0L);
        LogCore.core.info("OnlineService 清理 PlayerService 绑定: userId={}, gateSessionId={}, playerService={}",
                userId, gateSessionId, expectedPlayerService);
        return true;
    }

    /** 摘除已经由调用方完成会话校验的正式在线状态。 */
    public void removeOnlineState(String userId) {
        SOnlineUserState state = userStates.remove(userId);
        if (state != null) {
            removeActorAddresses(state.getActivePlayerId());
            state.setActivePlayerActorAddress(null);
            state.setActiveGateActorAddress(null);
        }
    }

    /** 清理当前用户登记的 OnlinePlayer；会话校验由调用方负责。 */
    public void removeOnlinePlayer(String userId) {
        SOnlineUserState state = userStates.get(userId);
        long playerId = state == null ? 0L : state.getActivePlayerId();
        onlinePlayerRegistry.remove(playerId);
        if (state != null) {
            removeActorAddresses(playerId);
            state.setActivePlayerService(null);
            state.setActivePlayerActorAddress(null);
            state.setActiveGateActorAddress(null);
            state.setActivePlayerId(0L);
        }
    }

    /** 缓存当前玩家所在 Stage 的 ActorAddress。 */
    public boolean cacheStageActorAddress(long playerId, ActorAddress stageActorAddress) {
        if (playerId <= 0L || stageActorAddress == null) {
            return false;
        }
        owner().getMessageLocationSender().cache(ActorId.mapPlayer(playerId), stageActorAddress);
        return true;
    }

    /** 删除当前玩家所在 Stage 的 ActorAddress 缓存。 */
    public void removeStageActorAddress(long playerId) {
        owner().getMessageLocationSender().remove(ActorId.mapPlayer(playerId));
    }

    /** 在 OnlineService 协程上下文中校验并清理 PlayerService 绑定。 */
    public boolean clearPlayerServiceWithLock(String userId, CallPoint gate, long gateSessionId,
                                              CallPoint expectedPlayerService) {
        try (ContinuationLockScope ignored = owner().awaitCoroutineLockScope(LockType.LOGIN, userId)) {
            return clearPlayerService(userId, gate, gateSessionId, expectedPlayerService);
        } catch (CoroutineLockTimeoutException e) {
            LogCore.core.warn("OnlineService 玩家解绑协程锁等待超时: userId={}, gateSessionId={}, timeoutMillis={}",
                    userId, gateSessionId, e.getTimeoutMillis());
            return false;
        }
    }

    private void removeActorAddresses(long playerId) {
        if (playerId <= 0L) {
            return;
        }
        ActorId playerActorId = ActorId.player(playerId);
        ActorId gateActorId = ActorId.gate(playerId);
        owner().getMessageLocationSender().remove(playerActorId);
        owner().getMessageLocationSender().remove(gateActorId);
        LogCore.core.info("OnlineService 删除 ActorAddress 缓存: playerId={}, playerActorId={}, gateActorId={}",
                playerId, playerActorId, gateActorId);
    }

    private boolean isSessionMismatch(SOnlineUserState state, CallPoint gate, long gateSessionId) {
        return state == null || gate == null || !gate.equals(state.getActiveGate())
                || gateSessionId != state.getActiveGateSessionId();
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }

}
