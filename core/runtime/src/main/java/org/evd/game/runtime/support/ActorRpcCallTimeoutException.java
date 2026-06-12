package org.evd.game.runtime.support;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;

public class ActorRpcCallTimeoutException extends SysException {
    private final String serviceId;
    private final long waitId;
    private final long timeoutMillis;
    private final ActorId actorId;
    private final ActorAddress actorAddress;

    public ActorRpcCallTimeoutException(String serviceId, long waitId, long timeoutMillis, ActorId actorId, ActorAddress actorAddress) {
        super("actor rpc call timeout: service={}, waitId={}, timeoutMillis={}, actorId={}, toNode={}, toService={}, ownerInstanceId={}, mailBoxInstanceId={}",
                serviceId,
                waitId,
                timeoutMillis,
                actorId,
                actorAddress == null || actorAddress.getCallPoint() == null ? null : actorAddress.getCallPoint().nodeId,
                actorAddress == null || actorAddress.getCallPoint() == null ? null : actorAddress.getCallPoint().servId,
                actorAddress == null ? null : actorAddress.getOwnerInstanceId(),
                actorAddress == null ? null : actorAddress.getMailBoxInstanceId());
        this.serviceId = serviceId;
        this.waitId = waitId;
        this.timeoutMillis = timeoutMillis;
        this.actorId = actorId == null ? null : new ActorId(actorId);
        this.actorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
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

    public ActorId getActorId() {
        return actorId == null ? null : new ActorId(actorId);
    }

    public ActorAddress getActorAddress() {
        return actorAddress == null ? null : new ActorAddress(actorAddress);
    }
}
