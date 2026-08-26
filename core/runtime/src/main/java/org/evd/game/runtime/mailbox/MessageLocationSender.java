package org.evd.game.runtime.mailbox;

import org.evd.game.annotation.service.ServiceName;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.support.exception.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MessageLocationSender {
    private static final int RETRY_TIMES = 10;
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

        if (forceRefresh) {
            return queryAndCacheActorAddress(actorId);
        }

        ActorAddress cached = getCachedAddress(actorId);
        if (cached != null) {
            return cached;
        }
        cached = getCachedAddress(actorId);
        if (cached != null) {
            return cached;
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
        if (ownerService.continuationRuntime().isContinuation()) {
            _send(actorId, methodKey, params);
        } else {
            ownerService.launchCoroutine(() -> _send(actorId, methodKey, params));
        }
    }


    public void _send(ActorId actorId, int methodKey, Object[] params) {
        callWithRetry(actorId, (actorAddress, logicalActorId) -> {
            requireOwnerService().getMessageSender().send(actorAddress, logicalActorId, methodKey, params);
            return null;
        });
    }

    public void sendClientCmd(ActorId actorId, org.evd.game.runtime.client.ClientSessionRef session,
                              int msgId, Chunk body) {
        if (ownerService.continuationRuntime().isContinuation()) {
            _sendClientCmd(actorId, session, msgId, body);
        } else {
            ownerService.launchCoroutine(() -> _sendClientCmd(actorId, session, msgId, body));
        }
    }
    public void _sendClientCmd(ActorId actorId, org.evd.game.runtime.client.ClientSessionRef session,
                               int msgId, Chunk body) {
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
        if (!ownerService.getServiceType().selfManageActorAddress()) {
            return _callWithRetry(actorId, caller);
        } else {
            return singleCall(actorId, caller);
        }
    }

    private <T> T _callWithRetry(ActorId actorId, ActorAddressCaller<T> caller) {
        int failTimes = 0;

        /**
         * 这里加协程锁是因为
         * Location 有缓存
         * Actor 会迁移
         * 多个协程能并发发送
         * NotFound 后自动刷新并重试
         * 还希望尽量保持消息顺序
         */
        try (ContinuationLockScope ignored = ownerService.awaitCoroutineLockScope(LockType.LOCATION_CALL, actorId)) {
            /**
             *  同一 entityId 的 Location 消息需要串行发送。
             *  主要是 resolveActorAddress 这里再去远程查询这里不加锁会重复查询，
             *  然后还有refreshIfActorNotFound 这里，远程消息发送过去以后，发现actor不在里面;
             *  如果不管 refreshIfActorNotFound，那其实在 resolveActorAddress 里加锁就好了;
             *
             *  这里的迁移主要就是场景，这里的代码适用于，玩家数据在场景里，随着场景迁移; 像现在玩家数据主要保存在PlayerService里面的其实不需要重试;
             *  可以先写着，后面修改
             *
             *  这里有个问题就是低频协议发送没事，高频协议发送的话，像是场景消息，这里的协程锁会使性能大浮动下降;
             *  这个时候就需要直接就给目标发ActorAddress而不需要重试;
             *  找到解决方案了：给向客户端发的协议的connService注解里加一个参数，说可以不重试的发消息; 然后这个类里再加个不重试的方法;
             *  至于其他的不是给客户端发的 其他的send的，可以再看，其实可以统一看ActorType,只有标记了的ActorType，才会进行重试加锁;
             *  其他的统一失败就报错，这样就不用加锁了，挺好;下周来了实现
             *  最终的解决方案是看发送服务器，有些服务器会自己维护ActorAddress，所以在某些服务器上加是否需要ActorAddress重试就行了;
             *
             *
             *  还有一个问题这里真的需要一直加锁吗，但是不加锁的话，这个removeCachedAddress这里会把之前获取到的新的给删除;
             *  而且会有很多次这个;而且也有可能我好的请求回来，就把我刚替换的ActorAddress删除了;这个感觉就需要一个版本号了,感觉确实最简单的就是全部加锁;
             *  问了gpt感觉没有完美的解决方案;
             *
             *
             */
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
    }

    private <T> T singleCall(ActorId actorId, ActorAddressCaller<T> caller) {
        ActorAddress actorAddress = getCachedAddress(actorId);
        if (actorAddress == null) {
            throw RpcCallException.actorNotFound(actorId);
        }

        try {
            return caller.call(new ActorAddress(actorAddress), new ActorId(actorId));
        } catch (RuntimeException exception) {
            /*if (!refreshIfActorNotFound(actorId, exception)) {
                throw exception;
            }*/
            // 这里不能删除 可能会吧后面手动缓存的actorAddress删除;
            //removeCachedAddress(actorId);
            throw exception;
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
        if (ownerService.getServiceType().selfManageActorAddress()) {
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
