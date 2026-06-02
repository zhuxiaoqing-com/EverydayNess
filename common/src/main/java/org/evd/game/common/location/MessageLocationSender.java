package org.evd.game.common.location;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;

public class MessageLocationSender {
    private static final int RETRY_TIMES = 20;
    private static final long RETRY_INTERVAL_MILLIS = 500L;

    @FunctionalInterface
    public interface ActorAddressResolver {
        ActorAddress query(ActorId actorId);
    }

    @FunctionalInterface
    public interface ActorAddressCaller<T> {
        T call(ActorAddress actorAddress, ActorId actorId);
    }

    private final ActorAddressResolver actorAddressResolver;

    public MessageLocationSender(ActorAddressResolver actorAddressResolver) {
        if (actorAddressResolver == null) {
            throw new IllegalArgumentException("actorAddressResolver 不能为空");
        }
        this.actorAddressResolver = actorAddressResolver;
    }

    public ActorAddress get(ActorId actorId) {
        Service service = Service.getCurrent();
        return service.getCachedActorAddress(actorId);
    }

    public ActorAddress getOrQuery(ActorId actorId) {
        ActorAddress cached = this.get(actorId);
        if (cached != null) {
            return cached;
        }

        ActorAddress remote = actorAddressResolver.query(actorId);
        if (remote == null) {
            return null;
        }

        this.cache(actorId, remote);
        return new ActorAddress(remote);
    }

    public void cache(ActorId actorId, ActorAddress actorAddress) {
        Service.getCurrent().cacheActorAddress(actorId, actorAddress);
    }

    public void remove(ActorId actorId) {
        Service.getCurrent().removeActorAddress(actorId);
    }

    public ActorAddress refresh(ActorId actorId) {
        ActorAddress remote = actorAddressResolver.query(actorId);
        if (remote == null) {
            remove(actorId);
            return null;
        }

        cache(actorId, remote);
        return new ActorAddress(remote);
    }

    public void send(ActorId actorId, int methodKey, Object[] params) {
        callWithRetry(actorId, (actorAddress, logicalActorId) -> {
            Service.getCurrent().getMessageSender().send(actorAddress, logicalActorId, methodKey, params);
            return null;
        });
    }

    @SuppressWarnings({"unchecked"})
    public <T> T callWait(ActorId actorId, int methodKey, Object[] params) {
        return callWithRetry(actorId, (actorAddress, logicalActorId) ->
                (T) Service.getCurrent().getMessageSender().callWait(actorAddress, logicalActorId, methodKey, params));
    }

    @SuppressWarnings({"unchecked"})
    public <T> T callWait(ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        return callWithRetry(actorId, (actorAddress, logicalActorId) ->
                (T) Service.getCurrent().getMessageSender().callWait(actorAddress, logicalActorId, methodKey, params, timeoutMillis));
    }

    public <T> T callWithRetry(ActorId actorId, ActorAddressCaller<T> caller) {
        int failTimes = 0;

        while (true) {
            ActorAddress actorAddress = this.get(actorId);
            if (actorAddress == null) {
                actorAddress = this.refresh(actorId);
            }
            if (actorAddress == null) {
                throw RpcCallException.actorNotFound(actorId);
            }

            try {
                return caller.call(new ActorAddress(actorAddress), new ActorId(actorId));
            } catch (RuntimeException exception) {
                if (!refreshIfActorNotFound(actorId, exception)) {
                    throw exception;
                }

                ++failTimes;
                if (failTimes > RETRY_TIMES) {
                    this.remove(actorId);
                    throw exception;
                }

                Service.getCurrent().sleep(RETRY_INTERVAL_MILLIS);
            }
        }
    }

    private boolean refreshIfActorNotFound(ActorId actorId, RuntimeException exception) {
        if (!(exception instanceof RpcCallException rpcCallException)) {
            return false;
        }
        if (rpcCallException.getErrorCode() != RpcErrorCodes.ACTOR_NOT_FOUND) {
            return false;
        }
        refresh(actorId);
        return true;
    }
}
