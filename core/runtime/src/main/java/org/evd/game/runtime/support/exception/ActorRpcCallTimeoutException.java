package org.evd.game.runtime.support.exception;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.RpcErrorCodes;

public class ActorRpcCallTimeoutException extends SysException {
    private final String serviceId;
    private final long waitId;
    private final long timeoutMillis;
    private final int methodKey;
    private final ActorId actorId;
    private final ActorAddress actorAddress;

    public ActorRpcCallTimeoutException(String serviceId, long waitId, long timeoutMillis, int methodKey, ActorId actorId, ActorAddress actorAddress) {
        super(RpcErrorCodes.ACTOR_RPC_CALL_TIMEOUT,
                "actor rpc call timeout: service={}, waitId={}, timeoutMillis={}, methodKey={}, actorId={}, toNode={}, toService={}, mailBoxEpoch={}",
                serviceId,
                waitId,
                timeoutMillis,
                methodKey,
                actorId,
                actorAddress == null || actorAddress.getCallPoint() == null ? null : actorAddress.getCallPoint().nodeId,
                actorAddress == null || actorAddress.getCallPoint() == null ? null : actorAddress.getCallPoint().servId,
                actorAddress == null ? null : actorAddress.getMailBoxEpoch());
        this.serviceId = serviceId;
        this.waitId = waitId;
        this.timeoutMillis = timeoutMillis;
        this.methodKey = methodKey;
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

    public int getMethodKey() {
        return methodKey;
    }

    public ActorId getActorId() {
        return actorId == null ? null : new ActorId(actorId);
    }

    public ActorAddress getActorAddress() {
        return actorAddress == null ? null : new ActorAddress(actorAddress);
    }
}
