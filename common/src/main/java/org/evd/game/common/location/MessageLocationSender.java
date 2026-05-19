package org.evd.game.common.location;

import org.evd.game.common.mailbox.MailboxSender;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailboxKey;

/**
 * 兼容旧类名，内部直接复用新的通用 mailbox sender。
 */
public class MessageLocationSender {
    private final MailboxSender mailboxSender = new MailboxSender();

    public CallPoint get(long actorId) {
        return mailboxSender.player(actorId).get();
    }

    public CallPoint getOrQuery(long actorId) {
        return mailboxSender.player(actorId).getOrQuery();
    }

    public void cache(long actorId, CallPoint callPoint) {
        mailboxSender.player(actorId).cache(callPoint);
    }

    public void remove(long actorId) {
        mailboxSender.player(actorId).remove();
    }

    public CallPoint refresh(long actorId) {
        return mailboxSender.player(actorId).refresh();
    }

    public void send(long actorId, int methodKey, Object[] params) {
        mailboxSender.player(actorId).callWithRetry((callPoint, mailboxKey) -> {
            Service.getCurrent().locationCallWait(callPoint, mailboxKey, methodKey, params);
            return null;
        });
    }

    public <T> T callWait(long actorId, int methodKey, Object[] params) {
        return mailboxSender.player(actorId).callWithRetry((callPoint, mailboxKey) ->
                (T) Service.getCurrent().locationCallWait(callPoint, mailboxKey, methodKey, params));
    }

    public <T> T callWait(long actorId, int methodKey, Object[] params, long timeoutMillis) {
        return mailboxSender.player(actorId).callWithRetry((callPoint, mailboxKey) ->
                (T) Service.getCurrent().locationCallWait(callPoint, mailboxKey, methodKey, params, timeoutMillis));
    }

    public <T> T callWithRetry(long actorId, LocationCaller<T> caller) {
        return mailboxSender.player(actorId).callWithRetry((callPoint, mailboxKey) -> caller.call(callPoint));
    }

    public <T> T callWithRetry(long actorId, MailboxLocationCaller<T> caller) {
        return mailboxSender.player(actorId).callWithRetry(caller::call);
    }

    @FunctionalInterface
    public interface LocationCaller<T> {
        T call(CallPoint callPoint);
    }

    @FunctionalInterface
    public interface MailboxLocationCaller<T> {
        T call(CallPoint callPoint, MailboxKey mailboxKey);
    }
}
