package org.evd.game.runtime;

import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.DispatchType;
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
        send(buildCall(toCallPoint, methodKey, params));
    }

    Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return callWait(toCallPoint, methodKey, params, service.getCallWaitTimeout());
    }

    Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        ContinuationRuntime continuationRuntime = service.continuationRuntimeInternal();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        CallPoint targetCallPoint = new CallPoint(toCallPoint);
        long waitId = continuationRuntime.registerWait(timeoutMillis, service.getWaitBaseTimeInternal(),
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new RpcCallTimeoutException(service.id, timeoutWaitId, timeoutMillis, targetCallPoint, methodKey)));

        Call call = buildCall(toCallPoint, methodKey, params);
        call.id = waitId;
        call.needResult = true;

        if (!send(call)) {
            continuationRuntime.takeWaitContinuation(waitId);
            throw new SysException("send rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    service.id, toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }

        return continuation.waitResult();
    }

    void sendClientCmd(CallPoint toCallPoint, ClientSessionRef session, int msgId, Chunk body) {
        Call call = new Call();
        call.from = copyLocalCallPoint();
        call.to = toCallPoint;
        call.dispatchType = DispatchType.CLIENT_CMD;
        call.methodKey = msgId;
        call.methodParam = new Object[]{session, body};
        call.needResult = false;
        send(call);
    }

    boolean send(CallBase call) {
        return service.sendOutboundCall(call);
    }

    private Call buildCall(CallPoint toCallPoint, int methodKey, Object[] params) {
        Call call = new Call();
        call.from = copyLocalCallPoint();
        call.to = toCallPoint;
        call.methodKey = methodKey;
        call.methodParam = params;
        return call;
    }

    private CallPoint copyLocalCallPoint() {
        return service.copyCallPoint();
    }
}
