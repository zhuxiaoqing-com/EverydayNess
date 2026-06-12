package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.CoroutineLockManager;
import org.evd.game.runtime.Service;
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

    public void dispatch(MailBoxComponent mailBox, ActorMessage message) {
        ContinuationRuntime continuationRuntime = service.continuationRuntimeInternal();
        Task.ContinuationWrapper continuation = continuationRuntime.create(
                () -> handle(mailBox, message),
                message.getActorId());
        continuation.bindDebugInfo(new Task.RpcDebugInfo(message.getMethodKey()));
        continuationRuntime.queue(continuation, "rpc");
    }

    private void handle(MailBoxComponent mailBox, ActorMessage message) {
        ContinuationRuntime continuationRuntime = service.continuationRuntimeInternal();
        CoroutineLockManager lockManager = service.coroutineLockManagerInternal();
        long ownerInstanceId = mailBox.getOwnerInstanceId();
        Task.ContinuationWrapper continuation = continuationRuntime.requireRunning();
        lockManager.await(Service.COROUTINE_LOCK_TYPE_MAILBOX, ownerInstanceId);
        try {
            if (!service.actorRegistryInternal().hasSameMailBoxInstance(ownerInstanceId, message.getMailBoxInstanceId())) {
                processInnerSender.replyActorNotFound(message);
                return;
            }
            processInnerSender.dispatchMailboxMessage(message);
        } finally {
            lockManager.release(continuation);
        }
    }
}
