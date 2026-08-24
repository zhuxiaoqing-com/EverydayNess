package org.evd.game.ConnService.session;

import java.util.HashMap;
import java.util.Map;

/** ConnService 的当前用户、玩家与网关会话索引，不承载登录或登出流程。 */
public final class ConnSessionRegistry {
    private final Map<String, Long> userChannelIds = new HashMap<>();
    private final Map<Long, Long> playerChannelIds = new HashMap<>();

    public Long findUserSessionId(String userId) {
        return userChannelIds.get(userId);
    }

    public Long findPlayerSessionId(long playerId) {
        return playerChannelIds.get(playerId);
    }

    public Long bindUserSession(String userId, long sessionId) {
        return userChannelIds.put(userId, sessionId);
    }

    public Long findPlayerSessionIdIfBound(long playerId) {
        return playerId > 0L ? playerChannelIds.get(playerId) : null;
    }

    public void bindPlayerSession(long playerId, long sessionId) {
        if (playerId > 0L) {
            playerChannelIds.put(playerId, sessionId);
        }
    }

    public boolean removePlayerSession(long playerId, long sessionId) {
        return playerId > 0L && playerChannelIds.remove(playerId, sessionId);
    }

    public void removeUserSession(String userId, long sessionId) {
        userChannelIds.remove(userId, sessionId);
    }

    public boolean isCurrentUserSession(String userId, long sessionId) {
        return userId != null && Long.valueOf(sessionId).equals(userChannelIds.get(userId));
    }
}
