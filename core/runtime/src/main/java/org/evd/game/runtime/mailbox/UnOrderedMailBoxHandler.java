package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.continuation.Task;

public final class UnOrderedMailBoxHandler {
    private final Service service;

    public UnOrderedMailBoxHandler(Service service) {
        this.service = service;
    }

    public void dispatch(MailBoxComponent mailBox, ActorMessage message) {
        Task.ContinuationWrapper continuation = service.createActorMessageContinuation(
                () -> handle(mailBox, message),
                message);
        service.queueContinuation(continuation);
    }

    private void handle(MailBoxComponent mailBox, ActorMessage message) {
        if (!service.hasSameMailBoxInstance(mailBox.getOwnerInstanceId(), message.getMailBoxInstanceId())) {
            service.replyActorNotFound(message);
            return;
        }
        service.dispatchMailBoxMessage_st(message);
    }
}
