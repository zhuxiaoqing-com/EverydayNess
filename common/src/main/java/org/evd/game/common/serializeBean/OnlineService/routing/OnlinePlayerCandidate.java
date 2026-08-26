package org.evd.game.common.serializeBean.OnlineService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

@SerializeClass
public class OnlinePlayerCandidate implements ISerializable {
    private CallPoint callPoint;
    private int onlineCount;

    public OnlinePlayerCandidate() {
    }

    public OnlinePlayerCandidate(CallPoint callPoint, int onlineCount) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
        this.onlineCount = onlineCount;
    }

    public CallPoint getCallPoint() {
        return callPoint == null ? null : new CallPoint(callPoint);
    }

    public void setCallPoint(CallPoint callPoint) {
        this.callPoint = callPoint == null ? null : new CallPoint(callPoint);
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(int onlineCount) {
        this.onlineCount = onlineCount;
    }
}
