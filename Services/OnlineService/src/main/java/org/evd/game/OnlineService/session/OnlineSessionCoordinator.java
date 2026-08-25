package org.evd.game.OnlineService.session;

import org.evd.game.common.serializeBean.OnlineService.session.OnlineUserState;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** OnlineService 的正式在线状态和玩家服务绑定状态源。 */
public final class OnlineSessionCoordinator {
    private final UserIdPlayerServiceMap historicalPlayerServiceMap = new UserIdPlayerServiceMap();
    private final UserIdConnServiceMap historicalConnServiceMap = new UserIdConnServiceMap();
    private final OnlinePlayerRegistry onlinePlayerRegistry = new OnlinePlayerRegistry();
    private final Map<String, OnlineUserState> userStates = new HashMap<>();

    /** 创建正式在线会话状态协调器。 */
    public OnlineSessionCoordinator() {
    }

    /** 返回用户正式在线状态的副本，避免外部修改内部会话。 */
    public OnlineUserState getUserState(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userStates.get(userId);
    }

    /** 由 OnlineService 内部状态源校验用户当前 Gate/Session。 */
    public boolean matchesSession(String userId, CallPoint gate, long gateSessionId) {
        return !isSessionMismatch(userStates.get(userId), gate, gateSessionId);
    }

    /** 判断用户是否不存在正式在线会话。 */
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

    /** 返回 OnlineService 当前登记的在线玩家数量。 */
    public int onlinePlayerCount() {
        return onlinePlayerRegistry.size();
    }

    /** 返回用户当前登记的在线玩家，包括尚未完成 PlayerService RPC 的上线流程。 */
    public OnlinePlayer getOnlinePlayer(String userId) {
        OnlineUserState userState = userStates.get(userId);
        if (userState == null || userState.getActivePlayerId() <= 0L) {
            return null;
        }
        OnlinePlayer onlinePlayer = onlinePlayerRegistry.get(userState.getActivePlayerId());
        return onlinePlayer != null && userId.equals(onlinePlayer.getUserId()) ? onlinePlayer : null;
    }

    /** 返回当前在线状态的只读集合视图，不复制集合；调用方不能修改集合结构。 */
    public Collection<OnlineUserState> getUserStates() {
        return Collections.unmodifiableCollection(userStates.values());
    }

    /** 返回用户历史绑定的 PlayerService，用于同一玩家优先回到原服务。 */
    public CallPoint getHistoricalPlayerService(String userId) {
        return historicalPlayerServiceMap.get(userId);
    }

    /** 推进用户到 ConnService、PlayerService 的过期映射清理。 */
    public void tick(long now) {
        historicalPlayerServiceMap.tick(now, this::isPlayerOffline);
        historicalConnServiceMap.tick(now, this::isPlayerOffline);
    }

    /** 创建新正式会话；网关 actor 和 Location 等选定 playerId 后再注册。 */
    public void createOnlineState(String userId, CallPoint gate, long gateSessionId) {
        OnlineUserState newState = new OnlineUserState(
                userId, gate, gateSessionId, 0L, null, null, null);
        userStates.put(userId, newState);
        historicalConnServiceMap.bind(userId, gate);
    }

    /**
     * 在调用 PlayerService 前登记 OnlinePlayer，OnlineService 以此作为玩家上线流程的拥有者。
     */
    public OnlinePlayer beginOnlinePlayer(String userId, CallPoint gate, long gateSessionId,
                                          long playerId, CallPoint playerService) {
        OnlineUserState userState = userStates.get(userId);
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
            // 保留 OnlineUserState 的兼容镜像；实际玩家生命周期以 OnlinePlayer 为准。
            userState.setActivePlayerId(playerId);
            userState.setActivePlayerService(playerService);
        }
        return onlinePlayer;
    }

    /** 将 PlayerService 返回的玩家 ActorAddress 登记到 OnlineUserState。 */
    public boolean bindPlayerActorAddress(OnlinePlayer onlinePlayer, ActorAddress actorAddress) {
        if (actorAddress == null || !onlinePlayerRegistry.isCurrent(onlinePlayer)) {
            return false;
        }
        OnlineUserState userState = userStates.get(onlinePlayer.getUserId());
        if (userState == null || getOnlinePlayer(onlinePlayer.getUserId()) != onlinePlayer) {
            return false;
        }
        onlinePlayer.markPlayerReady();
        userState.setActivePlayerActorAddress(actorAddress);
        historicalPlayerServiceMap.bind(onlinePlayer.getUserId(), userState.getActivePlayerService());
        Service.getCurrent().getMessageLocationSender().cache(
                ActorId.player(onlinePlayer.getPlayerId()), actorAddress);
        LogCore.core.info("OnlineService 缓存 PlayerActorAddress: userId={}, playerId={}, actorId={}, actorAddress={}",
                onlinePlayer.getUserId(), onlinePlayer.getPlayerId(),
                ActorId.player(onlinePlayer.getPlayerId()), actorAddress);
        return true;
    }

    /** 将 GW 返回的玩家 ActorAddress 登记到 OnlineUserState。 */
    public void bindGateActorAddress(OnlinePlayer onlinePlayer, ActorAddress actorAddress) {
        OnlineUserState userState = userStates.get(onlinePlayer.getUserId());
        onlinePlayer.markGateBound();
        userState.setActiveGateActorAddress(actorAddress);
        Service.getCurrent().getMessageLocationSender().cache(
                ActorId.gate(onlinePlayer.getPlayerId()), actorAddress);
        LogCore.core.info("OnlineService 缓存 GWActorAddress: userId={}, playerId={}, actorId={}, actorAddress={}",
                onlinePlayer.getUserId(), onlinePlayer.getPlayerId(),
                ActorId.gate(onlinePlayer.getPlayerId()), actorAddress);
    }

    /** 仅在网关会话匹配时清理在线状态，并返回已绑定的 PlayerService。 */
    public CallPoint clearSession(String userId, CallPoint gate, long gateSessionId) {
        OnlineUserState userState = userStates.get(userId);
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
        OnlineUserState userState = userStates.get(userId);
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
        historicalPlayerServiceMap.remove(userId);
        LogCore.core.info("OnlineService 清理 PlayerService 绑定: userId={}, gateSessionId={}, playerService={}",
                userId, gateSessionId, expectedPlayerService);
        return true;
    }

    /** 摘除已经由调用方完成会话校验的正式在线状态。 */
    public void removeOnlineState(String userId) {
        OnlineUserState state = userStates.remove(userId);
        if (state != null) {
            removeActorAddresses(state.getActivePlayerId());
            state.setActivePlayerActorAddress(null);
            state.setActiveGateActorAddress(null);
        }
    }

    /** 清理当前网关会话对应的 OnlinePlayer，旧会话不能删除新会话。 */
    public OnlinePlayer removeOnlinePlayer(String userId, CallPoint gate, long gateSessionId) {
        OnlineUserState state = userStates.get(userId);
        long playerId = state == null ? 0L : state.getActivePlayerId();
        OnlinePlayer onlinePlayer = onlinePlayerRegistry.remove(playerId);
        if (state != null) {
            removeActorAddresses(playerId);
            state.setActivePlayerService(null);
            state.setActivePlayerActorAddress(null);
            state.setActiveGateActorAddress(null);
            state.setActivePlayerId(0L);
        }
        return onlinePlayer;
    }

    /** 删除 OnlineService 自己维护的玩家和 GW ActorAddress 缓存。 */
    private void removeActorAddresses(long playerId) {
        if (playerId <= 0L) {
            return;
        }
        ActorId playerActorId = ActorId.player(playerId);
        ActorId gateActorId = ActorId.gate(playerId);
        Service.getCurrent().getMessageLocationSender().remove(playerActorId);
        Service.getCurrent().getMessageLocationSender().remove(gateActorId);
        LogCore.core.info("OnlineService 删除 ActorAddress 缓存: playerId={}, playerActorId={}, gateActorId={}",
                playerId, playerActorId, gateActorId);
    }

    /** 判断会话是否与指定网关和会话号不一致。 */
    private boolean isSessionMismatch(OnlineUserState state, CallPoint gate, long gateSessionId) {
        return state == null || gate == null || !gate.equals(state.getActiveGate())
                || gateSessionId != state.getActiveGateSessionId();
    }
}
