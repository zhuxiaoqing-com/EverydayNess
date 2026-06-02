package org.evd.game.runtime.continuation;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class CoroutineLockManager {
    private static final class LockKey {
        private final int type;
        private final Object key;

        private LockKey(int type, Object key) {
            this.type = type;
            this.key = key;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LockKey that)) {
                return false;
            }
            return type == that.type && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, key);
        }
    }

    private static final class LockQueue {
        private Task.ContinuationWrapper owner;
        private final ArrayDeque<Task.ContinuationWrapper> waiters = new ArrayDeque<>();
    }

    private final Map<LockKey, LockQueue> queues = new HashMap<>();
    private final IdentityHashMap<Task.ContinuationWrapper, LockKey> owners = new IdentityHashMap<>();

    public boolean tryAcquire(int type, Object key, Task.ContinuationWrapper continuation) {
        LockKey lockKey = new LockKey(type, key);
        LockQueue queue = queues.computeIfAbsent(lockKey, ignore -> new LockQueue());
        if (queue.owner == null) {
            queue.owner = continuation;
            owners.put(continuation, lockKey);
            return true;
        }
        if (queue.owner == continuation) {
            return true;
        }
        queue.waiters.addLast(continuation);
        return false;
    }

    public boolean owns(Task.ContinuationWrapper continuation) {
        return owners.containsKey(continuation);
    }

    public Task.ContinuationWrapper release(Task.ContinuationWrapper continuation) {
        LockKey lockKey = owners.remove(continuation);
        if (lockKey == null) {
            return null;
        }

        LockQueue queue = queues.get(lockKey);
        if (queue == null) {
            return null;
        }
        if (queue.owner != continuation) {
            throw new IllegalStateException("coroutine lock owner mismatch: " + lockKey.key);
        }

        Task.ContinuationWrapper next = queue.waiters.pollFirst();
        if (next == null) {
            queues.remove(lockKey);
            return null;
        }

        queue.owner = next;
        owners.put(next, lockKey);
        return next;
    }
}
