package org.evd.game.runtime.rpcProxyInterface;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.continuation.WaitType;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.support.exception.RpcCallTimeoutException;

public final class RpcOutboundGateway {
    private final Service service;

    public RpcOutboundGateway(Service service) {
        this.service = service;
    }

    public void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        send(CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, false, 0L));
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return callWait(toCallPoint, methodKey, params, service.getCallWaitTimeoutInternal());
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        CallPoint targetCallPoint = new CallPoint(toCallPoint);
        CallBase call = CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, true, 0L);
        ContinuationDebugInfo.ServiceRpcWaitDebugInfo debugInfo =
                new ContinuationDebugInfo.ServiceRpcWaitDebugInfo(targetCallPoint, methodKey, timeoutMillis);
        long waitId = continuationRuntime.registerWait(
                timeoutMillis,
                service.getWaitBaseTimeInternal(),
                WaitType.RPC,
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new RpcCallTimeoutException(service.getId(), timeoutWaitId, timeoutMillis, targetCallPoint, methodKey)),
                debugInfo);

        try {
            call.setId(waitId);
            send(call);
        } catch (Exception e) {
            continuationRuntime.cancelWait(waitId);
            throw e;
        }

        return continuation.waitResult();
    }

    public void sendClientCmd(CallPoint toCallPoint, ClientSessionRef session, int msgId, Chunk body) {
        send(CallFactory.buildServiceClientCmd(service, toCallPoint, session, msgId, body));
    }

    void send(CallBase call) {
        service.sendOutboundCall(call);
    }
}
