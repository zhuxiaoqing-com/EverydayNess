package org.evd.game.runtime.continuation;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.exception.CoroutineLockTimeoutException;

import java.util.*;


public final class CoroutineLockManager {
    public static final int DEFAULT_TIMEOUT_MILLIS = 60_000;

    private static final class LockKey {
        private final LockType type;
        private final Object key;

        private LockKey(LockType type, Object key) {
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

    public void await(LockType type, Object key) {
        await(type, key, DEFAULT_TIMEOUT_MILLIS);
    }

    public void await(LockType type, Object key, int timeoutMillis) {
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
        }, new ContinuationDebugInfo.LockWaitDebugInfo(type, key, timeoutMillis));
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
                : service._takeWaitContinuation(next.waitId);
        if (waitContinuation == null) {
            release(next.continuation);
            return;
        }

        waitContinuation.setResult(null);
        service._queueUnlockContinuation(waitContinuation);
    }

    private boolean tryAcquire(LockType type, Object key, Task.ContinuationWrapper continuation) {
        LockKey lockKey = new LockKey(type, key);
        LockKey ownedLock = owners.get(continuation);
        if (ownedLock != null) {
            // 表示当前协程 continuation 已经持有一把锁，现在又尝试获取另一把锁。
            // reentrant = 重复申请同一把锁;  nested    = 持有一把锁时，再申请另一把锁
            String violation = ownedLock.equals(lockKey) ? "reentrant" : "nested";
            throw new IllegalStateException(violation + " coroutine lock is forbidden: service=" + service.getId()
                    + ", conId=" + continuation.getConId()
                    + ", ownedType=" + ownedLock.type
                    + ", ownedKey=" + ownedLock.key
                    + ", requestedType=" + type
                    + ", requestedKey=" + key);
        }
        LockQueue queue = queues.computeIfAbsent(lockKey, ignore -> new LockQueue());
        if (queue.owner == null) {
            queue.owner = continuation;
            owners.put(continuation, lockKey);
            return true;
        }
        // 锁管理器自身状态损坏，属于实现 bug 或并发问题。
        if (queue.owner == continuation) {
            throw new IllegalStateException("coroutine lock owner index is inconsistent: service=" + service.getId()
                    + ", conId=" + continuation.getConId() + ", type=" + type + ", key=" + key);
        }
        return false;
    }

    private void addWaiter(LockType type, Object key, Task.ContinuationWrapper continuation, long waitId) {
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

    public String buildDebugDump() {
        Map<LockKey, LockQueue> queueSnapshot;
        try {
            queueSnapshot = new HashMap<>(queues);
        } catch (RuntimeException e) {
            return CoroutineLockDebugFormatter.buildDebugDump(
                    CoroutineLockDebugFormatter.snapshot(List.of(), e));
        }
        List<Map.Entry<LockKey, LockQueue>> entries = new ArrayList<>(queueSnapshot.entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<LockKey, LockQueue> left) -> left.getKey().type.code())
                .thenComparing(left -> String.valueOf(left.getKey().key)));
        List<CoroutineLockDebugFormatter.LockSnapshot> locks = new ArrayList<>(entries.size());
        for (Map.Entry<LockKey, LockQueue> entry : entries) {
            LockKey lockKey = entry.getKey();
            LockQueue lockQueue = entry.getValue();
            List<Task.ContinuationWrapper> waitersSnapshot = new ArrayList<>(lockQueue.waiters.size());
            for (WaitingContinuation waiter : lockQueue.waiters) {
                waitersSnapshot.add(waiter.continuation);
            }
            locks.add(CoroutineLockDebugFormatter.lockSnapshot(
                    lockKey.type,
                    lockKey.key,
                    lockQueue.owner,
                    waitersSnapshot));
        }
        return CoroutineLockDebugFormatter.buildDebugDump(
                CoroutineLockDebugFormatter.snapshot(locks, null));
    }
}
