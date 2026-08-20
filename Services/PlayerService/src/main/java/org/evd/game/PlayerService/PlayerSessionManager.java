package org.evd.game.PlayerService;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

/** PlayerService 的在线玩家绑定；玩家 Actor 和 Location 由 PlayerService 持有。 */
final class PlayerSessionManager {
    private record PlayerBinding(String userId, CallPoint gate, long gateSessionId,
                                 ActorAddress actorAddress, ActorAddress gateActorAddress) {
    }

    private final PlayerService owner;
    private final Map<Long, PlayerBinding> onlinePlayers = new HashMap<>();

    /** 创建 PlayerService 的玩家会话绑定管理器。 */
    PlayerSessionManager(PlayerService owner) {
        this.owner = owner;
    }

    /** 判断指定玩家是否已经在当前 PlayerService 上线。 */
    boolean hasOnlinePlayer(long playerId) {
        return onlinePlayers.containsKey(playerId);
    }

    /** 判断玩家正式上线通知是否仍匹配当前网关会话。 */
    boolean isCurrentSession(String userId, long playerId, ClientSessionRef session) {
        PlayerBinding binding = onlinePlayers.get(playerId);
        return binding != null && session != null
                && userId != null && userId.equals(binding.userId())
                && session.getGate() != null && session.getGate().equals(binding.gate())
                && session.getSessionId() == binding.gateSessionId();
    }

    /** 建立已完成参数和重复上线检查的玩家在线绑定。 */
    void bindPlayerSession(String userId, long playerId, ClientSessionRef session,
                              ActorAddress actorAddress) {
        CallPoint gate = session.getGate();
        long gateSessionId = session.getSessionId();
        PlayerBinding binding = new PlayerBinding(userId, gate, gateSessionId, actorAddress, null);
        onlinePlayers.put(playerId, binding);
        LogCore.core.info("PlayerService 绑定玩家: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                owner.getId(), userId, playerId, gate, gateSessionId);
    }

    /** 登记当前会话对应的 GW 玩家 ActorAddress。 */
    boolean bindGateActorAddress(String userId, long playerId, ClientSessionRef session,
                                 ActorAddress gateActorAddress) {
        PlayerBinding currentBinding = onlinePlayers.get(playerId);
        if (!isCurrentSession(userId, playerId, session) || gateActorAddress == null) {
            return false;
        }
        onlinePlayers.put(playerId, new PlayerBinding(
                currentBinding.userId(), currentBinding.gate(), currentBinding.gateSessionId(),
                currentBinding.actorAddress(), gateActorAddress));
        return true;
    }

    /** 仅清理仍匹配当前网关会话的玩家，避免旧会话误删新绑定。 */
    boolean onPlayerOffline(String userId, long playerId, CallPoint gate,
                            long gateSessionId, int brokenTypeCode) {
        PlayerBinding currentBinding = onlinePlayers.get(playerId);
        if (currentBinding == null || gate == null
                || !userId.equals(currentBinding.userId())
                || !gate.equals(currentBinding.gate())
                || currentBinding.gateSessionId() != gateSessionId) {
            LogCore.core.info("PlayerService 忽略旧 Session 下线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, currentBinding={}",
                    owner.getId(), userId, playerId, gate, gateSessionId, currentBinding);
            return false;
        }
        onlinePlayers.remove(playerId, currentBinding);
        LogCore.core.info("PlayerService 玩家离线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, brokenType={}",
                owner.getId(), userId, playerId, gate, gateSessionId,
                BrokenType.fromCode(brokenTypeCode));
        return true;
    }

    /** 返回当前 PlayerService 中已建立绑定的玩家数量。 */
    int getOnlineCount() {
        return onlinePlayers.size();
    }
}
