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
import org.evd.game.runtime.support.exception.SysException;

public final class RpcOutboundGateway {
    private final Service service;

    public RpcOutboundGateway(Service service) {
        this.service = service;
    }

    public void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        if (!send(CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, false, 0L))) {
            throw new SysException("send rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    service.getId(), toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return callWait(toCallPoint, methodKey, params, service.getCallWaitTimeoutInternal());
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        CallPoint targetCallPoint = new CallPoint(toCallPoint);
        long waitId = continuationRuntime.registerWait(
                timeoutMillis,
                service.getWaitBaseTimeInternal(),
                WaitType.RPC,
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new RpcCallTimeoutException(service.getId(), timeoutWaitId, timeoutMillis, targetCallPoint, methodKey)),
                new ContinuationDebugInfo.ServiceRpcWaitDebugInfo(targetCallPoint, methodKey, timeoutMillis));

        CallBase call = CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, true, waitId);
        if (!send(call)) {
            continuationRuntime.takeWaitContinuation(waitId);
            throw new SysException("send rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    service.getId(), toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }

        return continuation.waitResult();
    }

    public void sendClientCmd(CallPoint toCallPoint, ClientSessionRef session, int msgId, Chunk body) {
        if (!send(CallFactory.buildServiceClientCmd(service, toCallPoint, session, msgId, body))) {
            throw new SysException("send client cmd failed: service={}, toNode={}, toService={}, msgId={}",
                    service.getId(), toCallPoint.nodeId, toCallPoint.servId, msgId);
        }
    }

    boolean send(CallBase call) {
        return service.sendOutboundCall(call);
    }
}
