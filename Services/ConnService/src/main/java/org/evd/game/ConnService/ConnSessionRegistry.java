package org.evd.game.ConnService;

import java.util.HashMap;
import java.util.Map;

/** ConnService 的当前用户、玩家与网关会话索引，不承载登录或登出流程。 */
final class ConnSessionRegistry {
    private final Map<String, Long> userChannelIds = new HashMap<>();
    private final Map<Long, Long> playerChannelIds = new HashMap<>();

    Long findUserSessionId(String userId) {
        return userChannelIds.get(userId);
    }

    Long findPlayerSessionId(long playerId) {
        return playerChannelIds.get(playerId);
    }

    Long bindUserSession(String userId, long sessionId) {
        return userChannelIds.put(userId, sessionId);
    }

    Long findPlayerSessionIdIfBound(long playerId) {
        return playerId > 0L ? playerChannelIds.get(playerId) : null;
    }

    void bindPlayerSession(long playerId, long sessionId) {
        if (playerId > 0L) {
            playerChannelIds.put(playerId, sessionId);
        }
    }

    boolean removePlayerSession(long playerId, long sessionId) {
        return playerId > 0L && playerChannelIds.remove(playerId, sessionId);
    }

    void removeUserSession(String userId, long sessionId) {
        userChannelIds.remove(userId, sessionId);
    }

    boolean isCurrentUserSession(String userId, long sessionId) {
        return userId != null && Long.valueOf(sessionId).equals(userChannelIds.get(userId));
    }
}
