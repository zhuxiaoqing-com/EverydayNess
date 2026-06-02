package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.support.SysException;

public final class MessageSender {
    private final Service service;

    MessageSender(Service service) {
        this.service = service;
    }

    public void send(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params) {
        ActorMessage message = buildMessage(actorAddress, actorId, methodKey, params, false, 0L);
        if (!service.sendTransport_st(message)) {
            throw new SysException("send actor message failed: service={}, actorId={}, methodKey={}",
                    service.getId(), actorId, methodKey);
        }
    }

    public Object callWait(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params) {
        return callWait(actorAddress, actorId, methodKey, params, service.getTransportCallWaitTimeout());
    }

    public Object callWait(ActorAddress actorAddress, ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        Task.ContinuationWrapper continuation = service.requireRunningContinuationTransport();
        long waitId = service.registerTransportWait(timeoutMillis,
                (ctx, timeoutWaitId) -> ctx.setFailure(
                        new SysException("actor rpc call timeout: service={}, waitId={}, actorId={}",
                                service.getId(), timeoutWaitId, actorId)));

        ActorMessage message = buildMessage(actorAddress, actorId, methodKey, params, true, waitId);
        if (!service.sendTransport_st(message)) {
            service.takeTransportWaitContinuation(waitId);
            throw new SysException("send actor rpc call failed: service={}, actorId={}, methodKey={}",
                    service.getId(), actorId, methodKey);
        }
        return continuation.waitResult();
    }

    private ActorMessage buildMessage(
            ActorAddress actorAddress,
            ActorId actorId,
            int methodKey,
            Object[] params,
            boolean needResult,
            long waitId
    ) {
        if (actorAddress == null || actorAddress.getCallPoint() == null) {
            throw new SysException("actor address is null: actorId={}", actorId);
        }

        ActorMessage message = new ActorMessage();
        message.setFrom(service.getCallPointInternal());
        message.setTo(new CallPoint(actorAddress.getCallPoint()));
        message.setActorId(actorId == null ? null : new ActorId(actorId));
        message.setId(waitId);
        message.setOwnerInstanceId(actorAddress.getOwnerInstanceId());
        message.setMailBoxInstanceId(actorAddress.getMailBoxInstanceId());
        message.setMethodKey(methodKey);
        message.setMethodParam(params);
        message.setNeedResult(needResult);
        return message;
    }
}
