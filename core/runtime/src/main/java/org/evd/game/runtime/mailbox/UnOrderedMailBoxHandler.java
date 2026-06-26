package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.continuation.Task;

public final class UnOrderedMailBoxHandler {
    private final Service service;
    private final ProcessInnerSender processInnerSender;

    public UnOrderedMailBoxHandler(Service service, ProcessInnerSender processInnerSender) {
        this.service = service;
        this.processInnerSender = processInnerSender;
    }

    public void dispatch(MailBoxBean mailBox, ActorMessage message) {
        service.continuationRuntime().createAndEnterQueue(
                () -> handle(mailBox, message),
                message.getActorId(),Task.Reason.RPC, new Task.RpcDebugInfo(message.getMethodKey()));
    }

    private void handle(MailBoxBean mailBox, ActorMessage message) {
        if (!service.actorMailBoxRegistry().hasSameMailBoxEpoch(
                mailBox.getActorId(),
                message.getMailBoxEpoch())) {
            processInnerSender.replyActorNotFound(message);
            return;
        }
        processInnerSender.dispatchMailboxMessage(message);
    }
}
