package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.CoroutineLockManager;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.continuation.Task;

public final class OrderedMailBoxHandler {
    private final Service service;

    public OrderedMailBoxHandler(Service service) {
        this.service = service;
    }

    public void dispatch(MailBoxComponent mailBox, ActorMessage message) {
        Task.ContinuationWrapper continuation = service.createActorMessageContinuation(
                () -> handle(mailBox, message),
                message);
        service.queueContinuation(continuation);
    }

    private void handle(MailBoxComponent mailBox, ActorMessage message) {
        Task.ContinuationWrapper continuation = service.requireRunningContinuationTransport();
        CoroutineLockManager lockManager = service.getCoroutineLockManager();
        lockManager.await(Service.COROUTINE_LOCK_TYPE_MAILBOX, mailBox.getOwnerInstanceId());
        try {
            if (!service.hasSameMailBoxInstance(mailBox.getOwnerInstanceId(), message.getMailBoxInstanceId())) {
                service.replyActorNotFound(message);
                return;
            }
            service.dispatchMailBoxMessage_st(message);
        } finally {
            lockManager.release(continuation);
        }
    }
}
