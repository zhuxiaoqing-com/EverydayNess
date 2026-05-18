package org.evd.game.runtime.support;

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
}
