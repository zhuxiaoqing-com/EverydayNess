package org.evd.game.runtime.support;

import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;

public class RpcCallTimeoutException extends SysException {
    private final String serviceId;
    private final long waitId;
    private final long timeoutMillis;
    private final CallPoint toCallPoint;
    private final ActorId actorId;

    public RpcCallTimeoutException(String serviceId, long waitId, long timeoutMillis, CallPoint toCallPoint, ActorId actorId) {
        super("rpc call timeout: service={}, waitId={}, timeoutMillis={}, toNode={}, toService={}, actorId={}",
                serviceId,
                waitId,
                timeoutMillis,
                toCallPoint == null ? null : toCallPoint.nodeId,
                toCallPoint == null ? null : toCallPoint.servId,
                actorId);
        this.serviceId = serviceId;
        this.waitId = waitId;
        this.timeoutMillis = timeoutMillis;
        this.toCallPoint = toCallPoint == null ? null : new CallPoint(toCallPoint);
        this.actorId = actorId == null ? null : new ActorId(actorId);
    }

    public String getServiceId() {
        return serviceId;
    }

    public long getWaitId() {
        return waitId;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public CallPoint getToCallPoint() {
        return toCallPoint == null ? null : new CallPoint(toCallPoint);
    }

    public ActorId getActorId() {
        return actorId == null ? null : new ActorId(actorId);
    }
}
