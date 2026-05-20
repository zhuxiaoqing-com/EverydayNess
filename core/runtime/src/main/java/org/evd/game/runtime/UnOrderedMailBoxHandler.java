package org.evd.game.runtime;

import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.mailbox.MailBoxComponent;

final class UnOrderedMailBoxHandler {
    private final Service service;

    UnOrderedMailBoxHandler(Service service) {
        this.service = service;
    }

    void dispatch(MailBoxComponent mailBox, ActorMessage message) {
        Task.ContinuationWrapper continuation = service.createActorMessageContinuation(
                () -> handle(mailBox, message),
                message.getActorId());
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
