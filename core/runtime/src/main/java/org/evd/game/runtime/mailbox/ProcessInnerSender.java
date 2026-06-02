package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.call.CallResult;

import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;

public final class ProcessInnerSender {
    private final Service service;
    private final OrderedMailBoxHandler orderedMailBoxHandler;
    private final UnOrderedMailBoxHandler unOrderedMailBoxHandler;

    public ProcessInnerSender(Service service) {
        this.service = service;
        this.orderedMailBoxHandler = new OrderedMailBoxHandler(service);
        this.unOrderedMailBoxHandler = new UnOrderedMailBoxHandler(service);
    }

    public void dispatch(ActorMessage message) {
        MailBoxComponent mailBox = service.getMailBox(message.getOwnerInstanceId());
        if (mailBox == null) {
            replyActorNotFound(message);
            return;
        }

        if (mailBox.getMailBoxType() == MailBoxType.ORDERED) {
            orderedMailBoxHandler.dispatch(mailBox, message);
            return;
        }
        unOrderedMailBoxHandler.dispatch(mailBox, message);
    }

    public void replyActorNotFound(ActorMessage message) {
        if (!message.isNeedResult()) {
            return;
        }
        CallResult response = message.createReturn();
        response.setSuccess(false);
        response.setErrorCode(RpcErrorCodes.ACTOR_NOT_FOUND);
        response.setErrorMessage(RpcCallException.actorNotFound(message.getActorId()).getMessage());
        service.sendTransport_st(response);
    }
}
