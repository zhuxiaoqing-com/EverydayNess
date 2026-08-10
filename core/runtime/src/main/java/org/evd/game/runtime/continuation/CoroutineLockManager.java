package org.evd.game.runtime.continuation;

import org.evd.game.runtime.support.exception.CoroutineLockTimeoutException;
import org.evd.game.runtime.util.TimerScheduler;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;


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
        private final long timerId;

        private ReadyContinuation(Task.ContinuationWrapper continuation,
                                  long timerId) {
            this.continuation = continuation;
            this.timerId = timerId;
        }
    }

    private static final class WaitingContinuation {
        private final Task.ContinuationWrapper continuation;
        private final LockKey lockKey;
        private final long waitId;
        private long timerId;

        private WaitingContinuation(Task.ContinuationWrapper continuation,
                                    LockKey lockKey,
                                    long waitId) {
            this.continuation = continuation;
            this.lockKey = lockKey;
            this.waitId = waitId;
        }
    }

    private static final class LockQueue {
        private Task.ContinuationWrapper owner;
        private final ArrayDeque<WaitingContinuation> waiters = new ArrayDeque<>();
    }

    private final TimerScheduler timerScheduler;
    private final ContinuationRuntime continuationRuntime;
    private final Supplier<Task.ContinuationWrapper> currentContinuation;
    private final LongSupplier now;
    private final String serviceId;
    private final Map<LockKey, LockQueue> queues = new HashMap<>();
    private final HashMap<Task.ContinuationWrapper, LockKey> owners = new HashMap<>();
    private long waitIdAlloc = 1L;
    private boolean closed;

    public CoroutineLockManager(TimerScheduler timerScheduler,
                                ContinuationRuntime continuationRuntime,
                                Supplier<Task.ContinuationWrapper> currentContinuation,
                                LongSupplier now,
                                String serviceId) {
        this.timerScheduler = timerScheduler;
        this.continuationRuntime = continuationRuntime;
        this.currentContinuation = currentContinuation;
        this.now = now;
        this.serviceId = serviceId;
    }

    public void await(LockType type, Object key) {
        await(type, key, DEFAULT_TIMEOUT_MILLIS);
    }

    public void await(LockType type, Object key, int timeoutMillis) {
        if (closed) {
            throw new IllegalStateException("coroutine lock manager is closed: service=" + serviceId);
        }
        Task.ContinuationWrapper continuation = currentContinuation.get();
        if (tryAcquire(type, key, continuation)) {
            return;
        }

        continuation.prepareWait();
        continuation.markWaiting(new ContinuationDebugInfo.LockWaitDebugInfo(type, key, timeoutMillis));
        WaitingContinuation waitingContinuation = addWaiter(
                type, key, continuation, waitIdAlloc++);
        try {
            if (timeoutMillis > 0L) {
                waitingContinuation.timerId = timerScheduler.scheduleDelay(
                        now.getAsLong(),
                        timeoutMillis,
                        () -> timeoutWait(type, key, timeoutMillis, waitingContinuation));
            }
        } catch (RuntimeException failure) {
            cancelWait(waitingContinuation);
            throw failure;
        }
        continuation.waitResult();
    }

    public boolean owns(Task.ContinuationWrapper continuation) {
        return owners.containsKey(continuation);
    }

    public void release(Task.ContinuationWrapper continuation) {
        continuationRuntime.requireServiceThread();
        ReadyContinuation next = releaseOwner(continuation);
        if (next == null) {
            return;
        }

        if (next.timerId != 0L) {
            timerScheduler.cancel(next.timerId);
        }
        continuationRuntime.resume(
                next.continuation,
                null,
                Task.Reason.UNLOCK);
    }

    private boolean tryAcquire(LockType type, Object key, Task.ContinuationWrapper continuation) {
        LockKey lockKey = new LockKey(type, key);
        LockKey ownedLock = owners.get(continuation);
        if (ownedLock != null) {
            // 表示当前协程 continuation 已经持有一把锁，现在又尝试获取另一把锁。
            // reentrant = 重复申请同一把锁;  nested    = 持有一把锁时，再申请另一把锁
            String violation = ownedLock.equals(lockKey) ? "reentrant" : "nested";
            throw new IllegalStateException(violation + " coroutine lock is forbidden: service=" + serviceId
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
            throw new IllegalStateException("coroutine lock owner index is inconsistent: service=" + serviceId
                    + ", conId=" + continuation.getConId() + ", type=" + type + ", key=" + key);
        }
        return false;
    }

    private WaitingContinuation addWaiter(LockType type,
                                          Object key,
                                          Task.ContinuationWrapper continuation,
                                          long waitId) {
        LockKey lockKey = new LockKey(type, key);
        LockQueue queue = queues.computeIfAbsent(lockKey, ignore -> new LockQueue());
        if (queue.owner == null) {
            throw new IllegalStateException("coroutine lock waiter must have owner first: " + key);
        }
        if (queue.owner == continuation) {
            throw new IllegalStateException("coroutine lock waiter cannot equal owner: " + key);
        }
        WaitingContinuation waitingContinuation = new WaitingContinuation(
                continuation, lockKey, waitId);
        queue.waiters.addLast(waitingContinuation);
        return waitingContinuation;
    }

    private boolean cancelWait(WaitingContinuation waitingContinuation) {
        LockKey lockKey = waitingContinuation.lockKey;
        LockQueue queue = queues.get(lockKey);
        if (queue == null) {
            return false;
        }

        Iterator<WaitingContinuation> iterator = queue.waiters.iterator();
        while (iterator.hasNext()) {
            WaitingContinuation waiter = iterator.next();
            if (waiter != waitingContinuation) {
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

        WaitingContinuation next = queue.waiters.pollFirst();
        if (next != null) {
            queue.owner = next.continuation;
            owners.put(next.continuation, lockKey);
            return new ReadyContinuation(next.continuation, next.timerId);
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

    public void close() {
        List<Long> timerIds = new ArrayList<>();
        for (LockQueue queue : queues.values()) {
            for (WaitingContinuation waiter : queue.waiters) {
                if (waiter.timerId != 0L) {
                    timerIds.add(waiter.timerId);
                }
            }
        }
        timerScheduler.cancelAll(timerIds);
        closed = true;
        queues.clear();
        owners.clear();
    }

    private void timeoutWait(LockType type,
                             Object key,
                             int timeoutMillis,
                             WaitingContinuation waitingContinuation) {
        if (!cancelWait(waitingContinuation)) {
            return;
        }
        continuationRuntime.fail(
                waitingContinuation.continuation,
                new CoroutineLockTimeoutException(
                        serviceId,
                        type,
                        key,
                        waitingContinuation.waitId,
                        timeoutMillis),
                Task.Reason.TIMER);
    }
}
