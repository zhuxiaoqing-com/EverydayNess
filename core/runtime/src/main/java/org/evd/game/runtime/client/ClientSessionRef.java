package org.evd.game.runtime.client;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/**
 * 客户端请求的连接上下文，由 ConnService 创建并在服务间透传。
 */
@SerializeClass
public class ClientSessionRef implements ISerializable {
    private CallPoint gate;
    private long sessionId;
    private long playerId;

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

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

}
