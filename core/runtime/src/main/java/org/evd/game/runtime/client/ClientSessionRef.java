package org.evd.game.runtime.client;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/**
 * 客户端会话的跨服务引用
 */
@SerializeClass
public class ClientSessionRef implements ISerializable {
    @SerializeField
    private CallPoint gate;
    @SerializeField
    private long sessionId;
    @SerializeField
    private long routeKey;

    public ClientSessionRef() {
    }

    public ClientSessionRef(CallPoint gate, long sessionId, long routeKey) {
        this.gate = gate;
        this.sessionId = sessionId;
        this.routeKey = routeKey;
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

    public long getRouteKey() {
        return routeKey;
    }

    public void setRouteKey(long routeKey) {
        this.routeKey = routeKey;
    }
}
