package org.evd.game.runtime.netty;

public enum BrokenType {
    /** 未设置断开原因。 */
    NONE(0),
    /** 客户端主动断开或正常关闭。 */
    CLIENT_CLOSE(1),
    /** Netty 通道发生异常。 */
    NETTY_EXCEPTION(2),
    /** 客户端消息流量超过限制。 */
    MSG_FLOW_LIMIT(3),
    /** 服务端主动踢出玩家。 */
    SERVER_KICK(4),
    /** 新登录会话替换旧登录会话。 */
    LOGIN_REPLACE(5),
    /** 登录令牌失效。 */
    TOKEN_EXPIRE(6),
    /** 客户端心跳超时。 */
    HEARTBEAT_TIMEOUT(7),
    /** 状态对账发现连接或玩家状态不一致。 */
    STATE_RECONCILE(8);

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
