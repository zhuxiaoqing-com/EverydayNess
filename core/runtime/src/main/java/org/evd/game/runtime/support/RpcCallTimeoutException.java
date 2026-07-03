package org.evd.game.runtime.support;

import org.evd.game.runtime.call.CallPoint;

public class RpcCallTimeoutException extends SysException {
    private final String serviceId;
    private final long waitId;
    private final long timeoutMillis;
    private final CallPoint toCallPoint;
    private final int methodKey;

    public RpcCallTimeoutException(String serviceId, long waitId, long timeoutMillis, CallPoint toCallPoint, int methodKey) {
        super(RpcErrorCodes.RPC_CALL_TIMEOUT,
                "rpc call timeout: service={}, waitId={}, timeoutMillis={}, toNode={}, toService={}, methodKey={}",
                serviceId,
                waitId,
                timeoutMillis,
                toCallPoint == null ? null : toCallPoint.nodeId,
                toCallPoint == null ? null : toCallPoint.servId,
                methodKey);
        this.serviceId = serviceId;
        this.waitId = waitId;
        this.timeoutMillis = timeoutMillis;
        this.toCallPoint = toCallPoint == null ? null : new CallPoint(toCallPoint);
        this.methodKey = methodKey;
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

    public int getMethodKey() {
        return methodKey;
    }
}
