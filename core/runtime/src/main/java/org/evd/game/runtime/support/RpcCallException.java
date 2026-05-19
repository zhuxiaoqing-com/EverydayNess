package org.evd.game.runtime.support;

import org.evd.game.runtime.actor.ActorId;

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

    public static RpcCallException actorNotFound(ActorId actorId) {
        return new RpcCallException(RpcErrorCodes.ACTOR_NOT_FOUND, "rpc actor not found: actorId=" + actorId);
    }

    public static RpcCallException actorTypeMismatch(ActorId actorId, Class<?> expectedType, Class<?> actualType) {
        return new RpcCallException(
                RpcErrorCodes.ACTOR_TYPE_MISMATCH,
                "rpc actor type mismatch: actorId=" + actorId
                        + ", expectedType=" + expectedType.getName()
                        + ", actualType=" + actualType.getName());
    }
}
