package org.evd.game.PlayerService.session;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MapRoute;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** PlayerService 的在线玩家绑定；玩家 Actor 和 Location 由 PlayerService 持有。 */
public final class PlayerSessionManager {
    private final Map<Long, PPlayerOnline> onlinePlayers = new HashMap<>();

    /** 创建只负责保存玩家会话绑定的状态仓库。 */
    public PlayerSessionManager() {
    }

    /** 判断指定玩家是否已经占用当前 PlayerService，包含登录中的绑定。 */
    public boolean hasOnlinePlayer(long playerId) {
        return onlinePlayers.containsKey(playerId);
    }

    public PPlayerOnline get(long playerId) {
        return onlinePlayers.get(playerId);
    }

    /** 校验当前登录流程仍持有同一个玩家会话。 */
    public boolean isCurrent(String userId, long playerId, ClientSessionRef session) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        return currentBinding != null && session != null
                && userId != null && userId.equals(currentBinding.getUserId())
                && session.getGate() != null && session.getGate().equals(currentBinding.getGate())
                && session.getSessionId() == currentBinding.getGateSessionId();
    }

    /** 建立已完成参数和重复上线检查的玩家在线绑定。 */
    public void bindPlayerSession(String userId, long playerId, ClientSessionRef session,
                                  ActorAddress actorAddress) {
        CallPoint gate = session.getGate();
        long gateSessionId = session.getSessionId();
        PPlayerOnline binding = new PPlayerOnline(userId, playerId, gate, gateSessionId, actorAddress,
                PPlayerOnline.Status.LOADING_DATA);
        onlinePlayers.put(playerId, binding);
    }

    public boolean markReadyIfCurrent(String userId, long playerId, ClientSessionRef session) {
        if (!isCurrent(userId, playerId, session)) {
            return false;
        }
        onlinePlayers.get(playerId).markReady();
        return true;
    }

    /** 按 playerId 登记当前玩家对应的 GW ActorAddress。 */
    public boolean bindGateActorAddress(long playerId, ActorAddress gateActorAddress) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        if (currentBinding == null) {
            return false;
        }
        return currentBinding.bindGateActorAddress(gateActorAddress);
    }

    public boolean beginEnterMap(long playerId) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        return currentBinding != null && currentBinding.beginEnterMap();
    }

    public boolean completeEnterMap(long playerId, MapRoute route) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        return currentBinding != null && currentBinding.completeEnterMap(route);
    }

    public long getMapEnterSeq(long playerId) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        return currentBinding == null ? 0L : currentBinding.getMapEnterSeq();
    }

    /** 将完成进入地图的当前绑定推进到正式在线状态。 */
    public boolean markOnline(long playerId) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        if (currentBinding == null) {
            return false;
        }
        return currentBinding.markOnline();
    }

    /** 仅清理仍匹配当前网关会话的玩家，避免旧会话误删新绑定。 */
    public boolean removeIfCurrent(String userId, long playerId, CallPoint gate,
                                  long gateSessionId) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        if (currentBinding == null || gate == null
                || !userId.equals(currentBinding.getUserId())
                || !gate.equals(currentBinding.getGate())
                || currentBinding.getGateSessionId() != gateSessionId) {
            return false;
        }
        onlinePlayers.remove(playerId, currentBinding);
        return true;
    }

    /** 返回当前 PlayerService 中已建立绑定的玩家数量。 */
    public int getOnlineCount() {
        return onlinePlayers.size();
    }

    /** 返回当前在线玩家视图，调用方只遍历，不修改集合结构。 */
    public Collection<PPlayerOnline> onlinePlayers() {
        return onlinePlayers.values();
    }
}
