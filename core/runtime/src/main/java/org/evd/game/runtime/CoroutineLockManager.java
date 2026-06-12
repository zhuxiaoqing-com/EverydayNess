package org.evd.game.runtime;

import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.support.CoroutineLockTimeoutException;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class CoroutineLockManager {
    public static final int DEFAULT_TIMEOUT_MILLIS = 60_000;

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

    private static final class ReadyContinuation {
        private final Task.ContinuationWrapper continuation;
        private final long waitId;

        private ReadyContinuation(Task.ContinuationWrapper continuation, long waitId) {
            this.continuation = continuation;
            this.waitId = waitId;
        }
    }

    private static final class WaitingContinuation {
        private final Task.ContinuationWrapper continuation;
        private final long waitId;

        private WaitingContinuation(Task.ContinuationWrapper continuation, long waitId) {
            this.continuation = continuation;
            this.waitId = waitId;
        }
    }

    private static final class LockQueue {
        private Task.ContinuationWrapper owner;
        private final ArrayDeque<WaitingContinuation> waiters = new ArrayDeque<>();
    }

    private final Service service;
    private final Map<LockKey, LockQueue> queues = new HashMap<>();
    private final IdentityHashMap<Task.ContinuationWrapper, LockKey> owners = new IdentityHashMap<>();
    private final IdentityHashMap<Task.ContinuationWrapper, LockKey> waiters = new IdentityHashMap<>();

    public CoroutineLockManager(Service service) {
        this.service = service;
    }

    public void await(int type, Object key) {
        await(type, key, DEFAULT_TIMEOUT_MILLIS);
    }

    public void await(int type, Object key, int timeoutMillis) {
        Task.ContinuationWrapper continuation = service.requireRunningContinuation();
        if (tryAcquire(type, key, continuation)) {
            return;
        }

        long waitId = service.registerWait(timeoutMillis, (ctx, timeoutWaitId) -> {
            if (!cancelWait(ctx)) {
                return;
            }
            ctx.setFailure(new CoroutineLockTimeoutException(
                    service.getId(),
                    type,
                    key,
                    timeoutWaitId,
                    timeoutMillis));
        });
        addWaiter(type, key, continuation, waitId);
        continuation.waitResult();
    }

    public boolean owns(Task.ContinuationWrapper continuation) {
        return owners.containsKey(continuation);
    }

    public void release(Task.ContinuationWrapper continuation) {
        ReadyContinuation next = releaseOwner(continuation);
        if (next == null) {
            return;
        }

        Task.ContinuationWrapper waitContinuation = next.waitId == 0
                ? next.continuation
                : service.takeWaitContinuation(next.waitId);
        if (waitContinuation == null) {
            release(next.continuation);
            return;
        }

        waitContinuation.setResult(null);
        service.queueLockContinuation(waitContinuation);
    }

    private boolean tryAcquire(int type, Object key, Task.ContinuationWrapper continuation) {
        LockKey lockKey = new LockKey(type, key);
        LockQueue queue = queues.computeIfAbsent(lockKey, ignore -> new LockQueue());
        if (queue.owner == null) {
            queue.owner = continuation;
            owners.put(continuation, lockKey);
            return true;
        }
        return queue.owner == continuation;
    }

    private void addWaiter(int type, Object key, Task.ContinuationWrapper continuation, long waitId) {
        LockKey lockKey = new LockKey(type, key);
        LockQueue queue = queues.computeIfAbsent(lockKey, ignore -> new LockQueue());
        if (queue.owner == null) {
            throw new IllegalStateException("coroutine lock waiter must have owner first: " + key);
        }
        if (queue.owner == continuation) {
            throw new IllegalStateException("coroutine lock waiter cannot equal owner: " + key);
        }
        if (waiters.put(continuation, lockKey) != null) {
            throw new IllegalStateException("coroutine lock waiter already registered: " + key);
        }
        queue.waiters.addLast(new WaitingContinuation(continuation, waitId));
    }

    private boolean cancelWait(Task.ContinuationWrapper continuation) {
        LockKey lockKey = waiters.remove(continuation);
        if (lockKey == null) {
            return false;
        }

        LockQueue queue = queues.get(lockKey);
        if (queue == null) {
            return false;
        }

        Iterator<WaitingContinuation> iterator = queue.waiters.iterator();
        while (iterator.hasNext()) {
            WaitingContinuation waiter = iterator.next();
            if (waiter.continuation != continuation) {
                continue;
            }
            iterator.remove();
            if (queue.owner == null && queue.waiters.isEmpty()) {
                queues.remove(lockKey);
            }
            return true;
        }
        return false;
    }

    private ReadyContinuation releaseOwner(Task.ContinuationWrapper continuation) {
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

        WaitingContinuation next;
        while ((next = queue.waiters.pollFirst()) != null) {
            if (waiters.remove(next.continuation) == null) {
                continue;
            }
            queue.owner = next.continuation;
            owners.put(next.continuation, lockKey);
            return new ReadyContinuation(next.continuation, next.waitId);
        }

        queue.owner = null;
        queues.remove(lockKey);
        return null;
    }
}
