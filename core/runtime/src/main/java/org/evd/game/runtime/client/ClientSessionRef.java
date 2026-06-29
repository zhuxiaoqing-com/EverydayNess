package org.evd.game.runtime.client;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/**
 * 客户端会话的跨服务引用。
 * 这里只保留可序列化的路由信息，不能直接复用持有 Netty 连接对象的 runtime.netty.Message。
 */
@SerializeClass
public class ClientSessionRef implements ISerializable {
    @SerializeField
    private CallPoint gate;
    @SerializeField
    private long sessionId;
    @SerializeField
    private boolean authorized;
    @SerializeField
    private long playerId;
    @SerializeField
    private String userId;

    public ClientSessionRef() {
    }

    public ClientSessionRef(CallPoint gate, long sessionId) {
        this.gate = gate;
        this.sessionId = sessionId;
    }

    public CallPoint getGate() {
        return gate;
    }

    public void setGate(CallPoint gate) {
        this.gate = gate;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public String getUserId() {
        return userId == null ? "" : userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
