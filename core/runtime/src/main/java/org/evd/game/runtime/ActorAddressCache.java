package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class ActorAddressCache {
    static final long IDLE_MILLIS = 30L * 60L * 1000L;
    static final long CLEANUP_INTERVAL_MILLIS = 5L * 60L * 1000L;

    private static final class CachedActorAddress {
        private final ActorAddress actorAddress;
        private long lastAccessTime;

        private CachedActorAddress(ActorAddress actorAddress, long lastAccessTime) {
            this.actorAddress = actorAddress;
            this.lastAccessTime = lastAccessTime;
        }
    }

    private final Map<ActorId, CachedActorAddress> cachedAddresses = new HashMap<>();

    ActorAddress get(ActorId actorId, long now) {
        if (actorId == null) {
            return null;
        }
        CachedActorAddress cachedActorAddress = cachedAddresses.get(actorId);
        if (cachedActorAddress == null) {
            return null;
        }
        cachedActorAddress.lastAccessTime = now;
        return new ActorAddress(cachedActorAddress.actorAddress);
    }

    void put(ActorId actorId, ActorAddress actorAddress, long now) {
        if (actorId == null || actorAddress == null) {
            return;
        }
        cachedAddresses.put(
                new ActorId(actorId),
                new CachedActorAddress(new ActorAddress(actorAddress), now));
    }

    void remove(ActorId actorId) {
        if (actorId == null) {
            return;
        }
        cachedAddresses.remove(actorId);
    }

    void cleanupIdle(long now) {
        if (cachedAddresses.isEmpty()) {
            return;
        }
        long expireBefore = now - IDLE_MILLIS;
        Iterator<Map.Entry<ActorId, CachedActorAddress>> iterator = cachedAddresses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ActorId, CachedActorAddress> entry = iterator.next();
            if (entry.getValue().lastAccessTime > expireBefore) {
                continue;
            }
            iterator.remove();
        }
    }

    void clear() {
        cachedAddresses.clear();
    }
}
