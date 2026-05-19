package org.evd.game.runtime.support;

import org.evd.game.runtime.mailbox.MailboxKey;

public class RpcCallException extends SysException {
    private final int errorCode;

    public RpcCallException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RpcCallException(int errorCode, Throwable cause, String message) {
        super(cause, message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public static RpcCallException actorNotFound(long actorId) {
        return new RpcCallException(RpcErrorCodes.ACTOR_NOT_FOUND, "rpc actor not found: actorId=" + actorId);
    }

    public static RpcCallException mailboxNotFound(MailboxKey mailboxKey) {
        return new RpcCallException(RpcErrorCodes.MAILBOX_NOT_FOUND, "rpc mailbox not found: mailboxKey=" + mailboxKey);
    }

    public static RpcCallException mailboxKindMismatch(MailboxKey mailboxKey, Class<?> expectedType, Class<?> actualType) {
        return new RpcCallException(
                RpcErrorCodes.MAILBOX_KIND_MISMATCH,
                "rpc mailbox kind mismatch: mailboxKey=" + mailboxKey
                        + ", expectedType=" + expectedType.getName()
                        + ", actualType=" + actualType.getName());
    }
}
