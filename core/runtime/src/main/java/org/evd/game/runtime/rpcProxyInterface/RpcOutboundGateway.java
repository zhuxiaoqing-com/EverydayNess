package org.evd.game.runtime.rpcProxyInterface;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.*;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.continuation.WaitType;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.support.exception.RpcCallTimeoutException;

public final class RpcOutboundGateway {
    private final Service service;

    public RpcOutboundGateway(Service service) {
        this.service = service;
    }

    public void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        send(CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, false, 0L));
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        Call call = CallFactory.buildServiceRpc(service, toCallPoint, methodKey, params, true, 0L);
        return callWait(call, timeoutMillis);
    }

    public Object callWait(RpcCallBase call, long timeoutMillis) {
        if (!call.isNeedResult()) {
            LogCore.core.error("rpc callWait rejected: needResult=false, service={}, callType={}, target={}, methodKey={}",
                    service.getId(), call.getClass().getSimpleName(), call.getTo(), call.getMethodKey());
            throw new SysException("rpc callWait requires needResult=true: service={}, callType={}, target={}, methodKey={}",
                    service.getId(), call.getClass().getSimpleName(), call.getTo(), call.getMethodKey());
        }
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        ContinuationDebugInfo.ServiceRpcWaitDebugInfo debugInfo =
                new ContinuationDebugInfo.ServiceRpcWaitDebugInfo(call, timeoutMillis);
        long waitId = continuationRuntime.registerWait(
                timeoutMillis,
                service.getWaitBaseTimeInternal(),
                WaitType.RPC,
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new RpcCallTimeoutException(service.getId(), timeoutWaitId, timeoutMillis, call.getTo(), call.getMethodKey())),
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
