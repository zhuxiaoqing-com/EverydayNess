package org.evd.game.runtime;

import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.support.RpcCallTimeoutException;
import org.evd.game.runtime.support.SysException;

final class RpcOutboundGateway {
    private final Service service;

    RpcOutboundGateway(Service service) {
        this.service = service;
    }

    void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        send(CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, false, 0L));
    }

    Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return callWait(toCallPoint, methodKey, params, service.getCallWaitTimeout());
    }

    Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        CallPoint targetCallPoint = new CallPoint(toCallPoint);
        long waitId = continuationRuntime.registerWait(timeoutMillis, service.getWaitBaseTimeInternal(),
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new RpcCallTimeoutException(service.id, timeoutWaitId, timeoutMillis, targetCallPoint, methodKey)));

        CallBase call = CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, true, waitId);

        if (!send(call)) {
            continuationRuntime.takeWaitContinuation(waitId);
            throw new SysException("send rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    service.id, toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }

        return continuation.waitResult();
    }

    void sendClientCmd(CallPoint toCallPoint, ClientSessionRef session, int msgId, Chunk body) {
        send(CallFactory.buildServiceClientCmd(service, toCallPoint, session, msgId, body));
    }

    boolean send(CallBase call) {
        return service.sendOutboundCall(call);
    }
}
