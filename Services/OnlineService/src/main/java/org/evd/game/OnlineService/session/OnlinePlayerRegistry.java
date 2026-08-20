package org.evd.game.OnlineService.session;

import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

/** OnlineService 的在线玩家登记表，以 playerId 作为唯一索引。 */
public final class OnlinePlayerRegistry {
    private final Map<Long, OnlinePlayer> playersById = new HashMap<>();

    /** 在调用 PlayerService 上线前按 playerId 登记玩家。 */
    public OnlinePlayer begin(String userId, long playerId) {
        if (userId == null || userId.isBlank() || playerId <= 0L) {
            LogCore.core.warn("OnlineService 登记玩家失败，参数非法: userId={}, playerId={}",
                    userId, playerId);
            return null;
        }
        if (playersById.containsKey(playerId)) {
            return null;
        }
        OnlinePlayer onlinePlayer = new OnlinePlayer(userId, playerId);
        playersById.put(playerId, onlinePlayer);
        LogCore.core.info("OnlineService 登记玩家上线: userId={}, playerId={}", userId, playerId);
        return onlinePlayer;
    }

    public OnlinePlayer get(long playerId) {
        return playerId > 0L ? playersById.get(playerId) : null;
    }

    /** 判断异步上线流程仍然对应当前玩家对象。 */
    public boolean isCurrent(OnlinePlayer onlinePlayer) {
        if (onlinePlayer == null) {
            return false;
        }
        return playersById.get(onlinePlayer.getPlayerId()) == onlinePlayer;
    }

    public boolean markOnline(OnlinePlayer onlinePlayer) {
        if (!isCurrent(onlinePlayer)) {
            return false;
        }
        onlinePlayer.markOnline();
        return true;
    }

    /** 移除当前玩家对象；会话匹配由 OnlineSessionCoordinator 负责。 */
    public OnlinePlayer remove(long playerId) {
        OnlinePlayer onlinePlayer = get(playerId);
        if (!isCurrent(onlinePlayer)) {
            return null;
        }
        playersById.remove(onlinePlayer.getPlayerId(), onlinePlayer);
        LogCore.core.info("OnlineService 移除在线玩家: userId={}, playerId={}, status={}",
                onlinePlayer.getUserId(), onlinePlayer.getPlayerId(),
                onlinePlayer.getStatus());
        return onlinePlayer;
    }

    public int size() {
        return playersById.size();
    }
}
