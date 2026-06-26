package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.CoroutineLockManager;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;

public final class OrderedMailBoxHandler {
    private final Service service;
    private final ProcessInnerSender processInnerSender;

    public OrderedMailBoxHandler(Service service, ProcessInnerSender processInnerSender) {
        this.service = service;
        this.processInnerSender = processInnerSender;
    }

    public void dispatch(MailBoxBean mailBox, ActorMessage message) {
        service.continuationRuntime().createAndEnterQueue(
                () -> handle(mailBox, message),
                message.getActorId(),Task.Reason.ORDER_RPC, new Task.RpcDebugInfo(message.getMethodKey()));
    }

    private void handle(MailBoxBean mailBox, ActorMessage message) {
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        CoroutineLockManager lockManager = service.coroutineLockManagerInternal();
        ActorId actorId = mailBox.getActorId();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        lockManager.await(Service.COROUTINE_LOCK_TYPE_MAILBOX, actorId);
        try {
            if (!service.actorRegistryInternal().hasSameMailBoxEpoch(actorId, message.getMailBoxEpoch())) {
                processInnerSender.replyActorNotFound(message);
                return;
            }
            processInnerSender.dispatchMailboxMessage(message);
        } finally {
            lockManager.release(continuation);
        }
    }
}
