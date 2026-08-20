package org.evd.game.runtime.netty;

public enum BrokenType {
    NONE(0),
    CLIENT_CLOSE(1),
    NETTY_EXCEPTION(2),
    MSG_FLOW_LIMIT(3),
    SERVER_KICK(4),
    LOGIN_REPLACE(5),
    TOKEN_EXPIRE(6),
    HEARTBEAT_TIMEOUT(7);

    private static final BrokenType[] cacheValues = values();

    private final int code;

    BrokenType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BrokenType fromCode(Integer code) {
        if (code == null || code < 0 || code >= cacheValues.length) {
            return NONE;
        }
        BrokenType value = cacheValues[code];
        return value == null ? NONE : value;
    }

}
