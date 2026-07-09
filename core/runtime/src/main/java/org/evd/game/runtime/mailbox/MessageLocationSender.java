package org.evd.game.runtime.mailbox;

import org.evd.game.annotation.ServiceName;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MessageLocationSender {
    private static final int RETRY_TIMES = 20;
    private static final long RETRY_INTERVAL_MILLIS = 500L;
    private static final long IDLE_MILLIS = 30L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 5L * 60L * 1000L;

    private static final class CachedActorAddress {
        private final ActorAddress actorAddress;
        private long lastAccessTime;

        private CachedActorAddress(ActorAddress actorAddress, long lastAccessTime) {
            this.actorAddress = actorAddress;
            this.lastAccessTime = lastAccessTime;
        }
    }

    private final Map<ActorId, CachedActorAddress> cachedAddresses = new HashMap<>();
    private Service ownerService;
    private final LocationInterface locationInterface;

    @FunctionalInterface
    public interface ActorAddressCaller<T> {
        T call(ActorAddress actorAddress, ActorId actorId);
    }

    public MessageLocationSender(Service service) {
        if (service == null) {
            throw new IllegalArgumentException("service 不能为空");
        }
        ownerService = service;
        locationInterface = (LocationInterface) ServiceName.getRpcProxyObj(ServiceName.LOCATION_SERVICE);
    }

    public void close() {
        if (ownerService == null) {
            return;
        }
        cachedAddresses.clear();
        ownerService = null;
    }

    public static long getCleanupIntervalMillis() {
        return CLEANUP_INTERVAL_MILLIS;
    }

    private org.evd.game.runtime.call.CallPoint locationServiceRemote() {
        Node node = Service.getCurrent().getNode();
        CallPoint callPoint = node.getAnyCallPointByType(ServiceType.LOC);
        if (callPoint == null) {
            throw new IllegalStateException(
                    "找不到 LocationService 服务路由: org.evd.game.LocationService.LocationService");
        }

        return callPoint;
    }

    private Service requireOwnerService() {
        Service service = ownerService;
        if (service == null) {
            throw new IllegalStateException("MessageLocationSender 尚未初始化");
        }
        return service;
    }

    private long now() {
        return requireOwnerService().getTimeCurrent();
    }

    public ActorAddress get(ActorId actorId) {
        return getCachedAddress(actorId);
    }

    public ActorAddress getOrQuery(ActorId actorId) {
        return resolveActorAddress(actorId, false);
    }

    public void cache(ActorId actorId, ActorAddress actorAddress) {
        cacheActorAddress(actorId, actorAddress);
    }

    public void remove(ActorId actorId) {
        removeCachedAddress(actorId);
    }

    public ActorAddress refresh(ActorId actorId) {
        return resolveActorAddress(actorId, true);
    }

    private ActorAddress resolveActorAddress(ActorId actorId, boolean forceRefresh) {
        if (actorId == null) {
            return null;
        }
        if (!forceRefresh) {
            ActorAddress cached = getCachedAddress(actorId);
            if (cached != null) {
                return cached;
            }
        }
        return queryAndCacheActorAddress(actorId);
    }

    private ActorAddress getCachedAddress(ActorId actorId) {
        if (actorId == null) {
            return null;
        }
        CachedActorAddress cachedActorAddress = cachedAddresses.get(actorId);
        if (cachedActorAddress == null) {
            return null;
        }
        cachedActorAddress.lastAccessTime = now();
        return new ActorAddress(cachedActorAddress.actorAddress);
    }

    private ActorAddress queryAndCacheActorAddress(ActorId actorId) {
        ActorAddress remote = queryActorAddress(actorId);
        if (remote == null) {
            removeCachedAddress(actorId);
            return null;
        }
        cacheActorAddress(actorId, remote);
        return new ActorAddress(remote);
    }

    private ActorAddress queryActorAddress(ActorId actorId) {
        return getLocationInterface().get(locationServiceRemote(), actorId);
    }

    private LocationInterface getLocationInterface() {
        requireOwnerService();
        return locationInterface;
    }

    private void cacheActorAddress(ActorId actorId, ActorAddress actorAddress) {
        if (actorId == null || actorAddress == null) {
            return;
        }
        cachedAddresses.put(
                new ActorId(actorId),
                new CachedActorAddress(new ActorAddress(actorAddress), now()));
    }

    private void removeCachedAddress(ActorId actorId) {
        if (actorId == null) {
            return;
        }
        cachedAddresses.remove(actorId);
    }

    public void send(ActorId actorId, int methodKey, Object[] params) {
        callWithRetry(actorId, (actorAddress, logicalActorId) -> {
            requireOwnerService().getMessageSender().send(actorAddress, logicalActorId, methodKey, params);
            return null;
        });
    }

    public void sendClientCmd(ActorId actorId, ClientSessionRef session, int msgId, Chunk body) {
        callWithRetry(actorId, (actorAddress, logicalActorId) -> {
            Service current = requireOwnerService();
            current.sendOutboundCall(CallFactory.buildActorClientCmd(
                    current,
                    actorAddress,
                    logicalActorId,
                    msgId,
                    session,
                    body));
            return null;
        });
    }

    @SuppressWarnings({"unchecked"})
    public <T> T callWait(ActorId actorId, int methodKey, Object[] params) {
        return callWithRetry(actorId, (actorAddress, logicalActorId) ->
                (T) requireOwnerService().getMessageSender().callWait(actorAddress, logicalActorId, methodKey, params));
    }

    @SuppressWarnings({"unchecked"})
    public <T> T callWait(ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        return callWithRetry(actorId, (actorAddress, logicalActorId) ->
                (T) requireOwnerService().getMessageSender().callWait(actorAddress, logicalActorId, methodKey, params, timeoutMillis));
    }

    private <T> T callWithRetry(ActorId actorId, ActorAddressCaller<T> caller) {
        int failTimes = 0;

        while (true) {
            ActorAddress actorAddress = resolveActorAddress(actorId, false);
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
                    removeCachedAddress(actorId);
                    throw exception;
                }

                requireOwnerService().sleep(RETRY_INTERVAL_MILLIS);
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
        resolveActorAddress(actorId, true);
        return true;
    }

    public void cleanupIdle() {
        if (cachedAddresses.isEmpty()) {
            return;
        }
        long expireBefore = now() - IDLE_MILLIS;
        Iterator<Map.Entry<ActorId, CachedActorAddress>> iterator = cachedAddresses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ActorId, CachedActorAddress> entry = iterator.next();
            if (entry.getValue().lastAccessTime > expireBefore) {
                continue;
            }
            iterator.remove();
        }
    }
}
