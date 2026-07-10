package org.evd.game.runtime.support.exception;

import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.RpcErrorCodes;

public class RpcCallException extends SysException {
    public RpcCallException(int errorCode, String message) {
        super(errorCode, message);
    }

    public RpcCallException(int errorCode, Throwable cause, String message) {
        super(errorCode, cause, message);
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
