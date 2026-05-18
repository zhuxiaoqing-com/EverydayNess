package org.evd.game.runtime;

/**
 * 客户端连接会话
 */
public class Session {
    private final long sessionId;
    private final String remoteAddress;

    public Session(long sessionId, String remoteAddress) {
        this.sessionId = sessionId;
        this.remoteAddress = remoteAddress;
    }

    public long getSessionId() {
        return sessionId;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }
}
