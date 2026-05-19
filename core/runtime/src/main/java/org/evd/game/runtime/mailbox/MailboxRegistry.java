package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.support.RpcCallException;

import java.util.HashMap;
import java.util.Map;

public class MailboxRegistry {
    public static final class Registration {
        private final Object mailbox;
        private final MailboxExecutionMode executionMode;

        private Registration(Object mailbox, MailboxExecutionMode executionMode) {
            this.mailbox = mailbox;
            this.executionMode = executionMode;
        }

        public Object getMailbox() {
            return mailbox;
        }

        public MailboxExecutionMode getExecutionMode() {
            return executionMode;
        }
    }

    private final Map<MailboxKey, Registration> mailboxes = new HashMap<>();

    public void register(MailboxKey key, Object mailbox, MailboxExecutionMode executionMode) {
        mailboxes.put(new MailboxKey(key), new Registration(mailbox, executionMode));
    }

    public void unregister(MailboxKey key) {
        mailboxes.remove(key);
    }

    public boolean contains(MailboxKey key) {
        return mailboxes.containsKey(key);
    }

    public Object get(MailboxKey key) {
        Registration registration = mailboxes.get(key);
        return registration == null ? null : registration.getMailbox();
    }

    public Registration requireRegistration(MailboxKey key) {
        Registration registration = mailboxes.get(key);
        if (registration == null) {
            throw RpcCallException.mailboxNotFound(key);
        }
        return registration;
    }

    public <T> T require(MailboxKey key, Class<T> type) {
        Object mailbox = requireRegistration(key).getMailbox();
        if (!type.isInstance(mailbox)) {
            throw RpcCallException.mailboxKindMismatch(key, type, mailbox.getClass());
        }
        return type.cast(mailbox);
    }
}
