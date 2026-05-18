package org.evd.game.common.location;

import org.evd.game.common.proxy.LocationServiceProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;

import java.util.HashMap;
import java.util.Map;

/**
 * ET 的 MessageLocationSender 的最小 Java 版：
 * 先查本地缓存，miss 再查 LocationService，并把结果回填到本地。
 */
public class MessageLocationSender {
    private static final int RETRY_TIMES = 20;
    private static final long RETRY_INTERVAL_MILLIS = 500L;

    @FunctionalInterface
    public interface LocationCaller<T> {
        T call(CallPoint callPoint);
    }

    private final Map<Long, CallPoint> actorLocations = new HashMap<>();

    public CallPoint get(long actorId) {
        CallPoint callPoint = actorLocations.get(actorId);
        return callPoint == null ? null : new CallPoint(callPoint);
    }

    public CallPoint getOrQuery(long actorId) {
        CallPoint cached = this.get(actorId);
        if (cached != null) {
            return cached;
        }

        CallPoint remote = LocationServiceProxy.inst().getActor(actorId);
        if (remote == null) {
            return null;
        }

        this.cache(actorId, remote);
        return new CallPoint(remote);
    }

    public void cache(long actorId, CallPoint callPoint) {
        actorLocations.put(actorId, new CallPoint(callPoint));
    }

    public void remove(long actorId) {
        actorLocations.remove(actorId);
    }

    public CallPoint refresh(long actorId) {
        CallPoint remote = LocationServiceProxy.inst().getActor(actorId);
        if (remote == null) {
            actorLocations.remove(actorId);
            return null;
        }
        actorLocations.put(actorId, new CallPoint(remote));
        return new CallPoint(remote);
    }

    public boolean refreshIfActorNotFound(long actorId, RuntimeException exception) {
        if (!(exception instanceof RpcCallException rpcCallException)) {
            return false;
        }
        if (rpcCallException.getErrorCode() != RpcErrorCodes.ACTOR_NOT_FOUND) {
            return false;
        }
        this.refresh(actorId);
        return true;
    }

    public <T> T callWithRetry(long actorId, LocationCaller<T> caller) {
        int failTimes = 0;

        while (true) {
            CallPoint callPoint = this.get(actorId);
            if (callPoint == null) {
                callPoint = this.refresh(actorId);
            }
            if (callPoint == null) {
                throw RpcCallException.actorNotFound(actorId);
            }

            try {
                return caller.call(new CallPoint(callPoint));
            } catch (RuntimeException exception) {
                if (!this.refreshIfActorNotFound(actorId, exception)) {
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
}
