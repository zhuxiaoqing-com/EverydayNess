package org.evd.game.common.serializeBean.OnlineService.routing;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

@SerializeClass
public class OnlineConnCandidate implements ISerializable {
    private CallPoint callPoint;
    private String publicAddr;
    private int loginCount;

    public OnlineConnCandidate() {
    }

    public OnlineConnCandidate(CallPoint callPoint, String publicAddr, int loginCount) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
        this.publicAddr = publicAddr;
        this.loginCount = loginCount;
    }

    public CallPoint getCallPoint() {
        return callPoint == null ? null : new CallPoint(callPoint);
    }

    public void setCallPoint(CallPoint callPoint) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
    }

    public String getPublicAddr() {
        return publicAddr;
    }

    public void setPublicAddr(String publicAddr) {
        this.publicAddr = publicAddr;
    }

    public int getLoginCount() {
        return loginCount;
    }

    public void setLoginCount(int loginCount) {
        this.loginCount = loginCount;
    }
}
