package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.DispatchType;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.support.RpcCallTimeoutException;
import org.evd.game.runtime.support.SysException;

final class RpcOutboundGateway {
    private final Service service;
    private final CallTransport callTransport;

    RpcOutboundGateway(Service service, CallTransport callTransport) {
        this.service = service;
        this.callTransport = callTransport;
    }

    void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        call(toCallPoint, null, methodKey, params);
    }

    void call(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        send(buildCall(toCallPoint, actorId, methodKey, params));
    }

    Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return callWait(toCallPoint, null, methodKey, params, service.getCallWaitTimeout());
    }

    Object callWait(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        return callWait(toCallPoint, actorId, methodKey, params, service.getCallWaitTimeout());
    }

    Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        return callWait(toCallPoint, null, methodKey, params, timeoutMillis);
    }

    Object callWait(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        Task.ContinuationWrapper continuation = service.requireRunningContinuation();
        CallPoint targetCallPoint = new CallPoint(toCallPoint);
        ActorId targetActorId = actorId == null ? null : new ActorId(actorId);
        long waitId = service.registerWait(timeoutMillis,
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new RpcCallTimeoutException(service.getId(), timeoutWaitId, timeoutMillis, targetCallPoint, targetActorId)));

        Call call = buildCall(toCallPoint, actorId, methodKey, params);
        call.id = waitId;
        call.needResult = true;

        if (!send(call)) {
            service.takeWaitContinuation(waitId);
            throw new SysException("send rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    service.getId(), toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }

        return continuation.waitResult();
    }

    void sendClientCmd(CallPoint toCallPoint, ActorId actorId, ClientSessionRef session, int msgId, Chunk body) {
        Call call = new Call();
        call.from = service.getCallPointInternal();
        call.to = toCallPoint;
        call.actorId = actorId == null ? null : new ActorId(actorId);
        call.dispatchType = DispatchType.CLIENT_CMD;
        call.methodKey = msgId;
        call.methodParam = new Object[]{session, body};
        call.needResult = false;
        send(call);
    }

    boolean send(CallBase call) {
        return callTransport.send(call);
    }

    private Call buildCall(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        Call call = new Call();
        call.from = service.getCallPointInternal();
        call.to = toCallPoint;
        call.actorId = actorId == null ? null : new ActorId(actorId);
        call.methodKey = methodKey;
        call.methodParam = params;
        return call;
    }
}
