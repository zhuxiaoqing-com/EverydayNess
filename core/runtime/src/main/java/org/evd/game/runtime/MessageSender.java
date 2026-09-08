package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.call.RpcCallBase;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.serializeBean.Chunk;
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
        ActorId targetActorId = actorId == null ? null : new ActorId(actorId);
        ActorAddress targetActorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
        RpcCallBase message = CallFactory.buildActorRpc(service, actorAddress, actorId, methodKey, params, true, 0L);
        ContinuationDebugInfo.ActorRpcWaitDebugInfo debugInfo =
                new ContinuationDebugInfo.ActorRpcWaitDebugInfo(targetActorId, targetActorAddress, methodKey, timeoutMillis);
        return service.getCallTransport().awaitRpcCall(
                message,
                timeoutMillis,
                debugInfo,
                timeoutWaitId -> new ActorRpcCallTimeoutException(
                        service.id, timeoutWaitId, timeoutMillis, methodKey, targetActorId, targetActorAddress));
    }

    public Object callClientCmdWait(ActorAddress actorAddress,
                                    ActorId actorId,
                                    ClientSessionRef session,
                                    int msgId,
                                    Chunk body) {
        return callClientCmdWait(actorAddress, actorId, session, msgId, body, service.getCallWaitTimeout());
    }

    public Object callClientCmdWait(ActorAddress actorAddress,
                                    ActorId actorId,
                                    ClientSessionRef session,
                                    int msgId,
                                    Chunk body,
                                    long timeoutMillis) {
        ActorId targetActorId = actorId == null ? null : new ActorId(actorId);
        ActorAddress targetActorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
        RpcCallBase message = CallFactory.buildActorClientCmd(
                service, actorAddress, actorId, msgId, session, body, true);
        ContinuationDebugInfo.ActorRpcWaitDebugInfo debugInfo =
                new ContinuationDebugInfo.ActorRpcWaitDebugInfo(targetActorId, targetActorAddress, msgId, timeoutMillis);
        return service.getCallTransport().awaitRpcCall(
                message,
                timeoutMillis,
                debugInfo,
                timeoutWaitId -> new ActorRpcCallTimeoutException(
                        service.id, timeoutWaitId, timeoutMillis, msgId, targetActorId, targetActorAddress));
    }
}
