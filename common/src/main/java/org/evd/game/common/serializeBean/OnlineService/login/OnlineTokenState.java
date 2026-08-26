package org.evd.game.common.serializeBean.OnlineService.login;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

@SerializeClass
public class OnlineTokenState implements ISerializable {
    private String token;
    private String userId;
    private CallPoint gate;
    private long expireAt;
    /** 仅供服务器之间校验的世代号，不下发给客户端。 */
    private long version;

    public OnlineTokenState() {
    }

    public OnlineTokenState(String token, String userId, CallPoint gate, long expireAt, long version) {
        this.token = token;
        this.userId = userId;
        this.gate = gate;
        this.expireAt = expireAt;
        this.version = version;
    }

    public OnlineTokenState(OnlineTokenState other) {
        this(other.token, other.userId, other.gate, other.expireAt, other.version);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public CallPoint getGate() {
        return gate;
    }

    public void setGate(CallPoint gate) {
        this.gate = gate;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
