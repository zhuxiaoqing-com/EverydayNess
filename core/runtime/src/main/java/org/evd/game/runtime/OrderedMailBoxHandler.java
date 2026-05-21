package org.evd.game.runtime;

import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.mailbox.MailBoxComponent;

final class OrderedMailBoxHandler {
    private final Service service;

    OrderedMailBoxHandler(Service service) {
        this.service = service;
    }

    void dispatch(MailBoxComponent mailBox, ActorMessage message) {
        Task.ContinuationWrapper continuation = service.createActorMessageContinuation(
                () -> handle(mailBox, message),
                message);
        service.queueContinuation(continuation);
    }

    private void handle(MailBoxComponent mailBox, ActorMessage message) {
        Task.ContinuationWrapper continuation = service.requireRunningContinuationTransport();
        service.awaitCoroutineLock(Service.COROUTINE_LOCK_TYPE_MAILBOX, mailBox.getOwnerInstanceId());
        try {
            if (!service.hasSameMailBoxInstance(mailBox.getOwnerInstanceId(), message.getMailBoxInstanceId())) {
                service.replyActorNotFound(message);
                return;
            }
            service.dispatchMailBoxMessage_st(message);
        } finally {
            service.releaseContinuationLock(continuation);
        }
    }
}
