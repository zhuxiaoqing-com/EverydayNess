package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.support.exception.ActorRpcCallTimeoutException;
import org.evd.game.runtime.support.exception.SysException;

public final class MessageSender {
    private final Service service;

    MessageSender(Service service) {
        this.service = service;
    }

    public void send(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params) {
        CallBase message = CallFactory.buildActorRpc(service, actorAddress, actorId, methodKey, params, false, 0L);
        if (!service.sendOutboundCall(message)) {
            throw new SysException("send actor message failed: service={}, actorId={}, methodKey={}",
                    service.id, actorId, methodKey);
        }
    }

    public Object callWait(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params) {
        return callWait(actorAddress, actorId, methodKey, params, service.getCallWaitTimeout());
    }

    public Object callWait(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        ActorId targetActorId = actorId == null ? null : new ActorId(actorId);
        ActorAddress targetActorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
        long waitId = continuationRuntime.registerWait(
                timeoutMillis,
                service.getWaitBaseTimeInternal(),
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new ActorRpcCallTimeoutException(service.id, timeoutWaitId, timeoutMillis, methodKey, targetActorId, targetActorAddress)),
                new ContinuationDebugInfo.ActorRpcWaitDebugInfo(targetActorId, targetActorAddress, methodKey, timeoutMillis));

        CallBase message = CallFactory.buildActorRpc(service, actorAddress, actorId, methodKey, params, true, waitId);
        if (!service.sendOutboundCall(message)) {
            continuationRuntime.takeWaitContinuation(waitId);
            throw new SysException("send actor rpc call failed: service={}, actorId={}, methodKey={}",
                    service.id, actorId, methodKey);
        }
        return continuation.waitResult();
    }
}
