package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.continuation.WaitType;
import org.evd.game.runtime.support.exception.ActorRpcCallTimeoutException;

public final class MessageSender {
    private final Service service;

    MessageSender(Service service) {
        this.service = service;
    }

    public void send(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params) {
        CallBase message = CallFactory.buildActorRpc(service, actorAddress, actorId, methodKey, params, false, 0L);
        service.sendOutboundCall(message);
    }

    public Object callWait(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params) {
        return callWait(actorAddress, actorId, methodKey, params, service.getCallWaitTimeout());
    }

    public Object callWait(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        ActorId targetActorId = actorId == null ? null : new ActorId(actorId);
        ActorAddress targetActorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
        CallBase message = CallFactory.buildActorRpc(service, actorAddress, actorId, methodKey, params, true, 0L);
        ContinuationDebugInfo.ActorRpcWaitDebugInfo debugInfo =
                new ContinuationDebugInfo.ActorRpcWaitDebugInfo(targetActorId, targetActorAddress, methodKey, timeoutMillis);
        long waitId = continuationRuntime.registerWait(
                timeoutMillis,
                service.getWaitBaseTimeInternal(),
                WaitType.RPC,
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new ActorRpcCallTimeoutException(service.id, timeoutWaitId, timeoutMillis, methodKey, targetActorId, targetActorAddress)),
                debugInfo);

        try {
            message.setId(waitId);
            service.sendOutboundCall(message);
        } catch (Exception e) {
            continuationRuntime.cancelWait(waitId);
            throw e;
        }
        return continuation.waitResult();
    }
}
