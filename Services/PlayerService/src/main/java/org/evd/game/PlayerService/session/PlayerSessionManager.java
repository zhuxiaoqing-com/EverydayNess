package org.evd.game.PlayerService.session;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.actor.ActorAddress;

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

    /** 建立已完成参数和重复上线检查的玩家在线绑定。 */
    public void bindPlayerSession(String userId, long playerId, ClientSessionRef session,
                                  ActorAddress actorAddress) {
        CallPoint gate = session.getGate();
        long gateSessionId = session.getSessionId();
        PPlayerOnline binding = new PPlayerOnline(userId, gate, gateSessionId, actorAddress,
                PPlayerOnline.Status.LOADING_DATA);
        onlinePlayers.put(playerId, binding);
    }

    /** 将已完成玩家数据加载的当前绑定推进到可上线状态。 */
    public boolean markReady(long playerId) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        if (currentBinding == null) {
            return false;
        }
        currentBinding.markReady();
        return true;
    }

    /** 按 playerId 登记当前玩家对应的 GW ActorAddress。 */
    public void bindGateActorAddress(long playerId, ActorAddress gateActorAddress) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        if (currentBinding == null) {
            return;
        }
        currentBinding.bindGateActorAddress(gateActorAddress);
    }

    /** 将完成进入地图的当前绑定推进到正式在线状态。 */
    public boolean markOnline(long playerId) {
        PPlayerOnline currentBinding = onlinePlayers.get(playerId);
        if (currentBinding == null) {
            return false;
        }
        currentBinding.markOnline();
        return true;
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

    /** 返回当前玩家绑定快照，调用方不能通过该副本修改内部绑定。 */
    public Map<Long, PPlayerOnline> snapshotBindings() {
        return Map.copyOf(onlinePlayers);
    }
}
