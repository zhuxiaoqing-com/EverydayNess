package org.evd.game.runtime.continuation;

import org.evd.game.runtime.support.exception.CoroutineLockTimeoutException;
import org.evd.game.runtime.util.TimerScheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class CoroutineLockManager {
    public static final int DEFAULT_TIMEOUT_MILLIS = 60_000;

    private static final class ReadyContinuation {
        private final Task.ContinuationWrapper continuation;
        private final long timerId;

        private ReadyContinuation(Task.ContinuationWrapper continuation,
                                  long timerId) {
            this.continuation = continuation;
            this.timerId = timerId;
        }
    }

    private final TimerScheduler timerScheduler;
    private final ContinuationRuntime continuationRuntime;
    private final Supplier<Task.ContinuationWrapper> currentContinuation;
    private final LongSupplier now;
    private final String serviceId;
    private final Map<CoroutineLock.Key, CoroutineLock> locks = new HashMap<>();
    private final Map<Task.ContinuationWrapper, Set<CoroutineLock>> ownedLocks = new HashMap<>();
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

    public ContinuationLockScope await(LockType type, Object key) {
        return await(type, key, DEFAULT_TIMEOUT_MILLIS);
    }

    public ContinuationLockScope await(LockType type, Object key, int timeoutMillis) {
        if (closed) {
            throw new IllegalStateException("coroutine lock manager is closed: service=" + serviceId);
        }
        if (key == null) {
            return new ContinuationLockScope(null, null, null);
        }
        Task.ContinuationWrapper continuation = Objects.requireNonNull(
                currentContinuation.get(), "current continuation is required");
        CoroutineLock lock = getOrCreateLock(type, key);
        switch (lock.acquire(continuation)) {
            case ACQUIRED -> {
                addOwnedLock(continuation, lock);
                return new ContinuationLockScope(this, continuation, lock);
            }
            case REENTRANT -> {
                return new ContinuationLockScope(this, continuation, lock);
            }
            case BUSY -> {
                // 由下面的等待流程排队。
            }
        }

        continuation.prepareWait();
        continuation.markWaiting(new ContinuationDebugInfo.LockWaitDebugInfo(type, key, timeoutMillis));
        CoroutineLock.WaitingContinuation waitingContinuation = lock.enqueue(
                continuation, waitIdAlloc++);
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
        return new ContinuationLockScope(this, continuation, lock);
    }

    private CoroutineLock getOrCreateLock(LockType type, Object key) {
        CoroutineLock.Key lockKey = new CoroutineLock.Key(type, key);
        return locks.computeIfAbsent(lockKey, CoroutineLock::new);
    }

    public boolean owns(Task.ContinuationWrapper continuation) {
        Set<CoroutineLock> locks = ownedLocks.get(continuation);
        return locks != null && !locks.isEmpty();
    }

    public String buildOwnedLockDebugInfo(Task.ContinuationWrapper continuation) {
        Set<CoroutineLock> owned = ownedLocks.get(continuation);
        if (owned == null || owned.isEmpty()) {
            return "none";
        }

        List<CoroutineLock> locks = new ArrayList<>(owned);
        locks.sort(Comparator
                .comparingInt((CoroutineLock lock) -> lock.key.type().code())
                .thenComparing(lock -> String.valueOf(lock.key.key())));

        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < locks.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            CoroutineLock lock = locks.get(i);
            result.append("{type=").append(lock.key.type())
                    .append(", key=").append(lock.key.key())
                    .append(", holdCount=").append(lock.holdCount)
                    .append(", waiters=").append(lock.waiters.size())
                    .append('}');
        }
        return result.append(']').toString();
    }

    void release(Task.ContinuationWrapper continuation, CoroutineLock lock) {
        continuationRuntime.requireServiceThread();
        Task.ContinuationWrapper current = continuationRuntime.requireRunning();
        if (current != continuation) {
            throw new IllegalStateException(
                    "coroutine lock scope must close in its owning continuation: service=" + serviceId
                            + ", ownerConId=" + continuation.getConId()
                            + ", currentConId=" + current.getConId());
        }
        ReadyContinuation next = releaseOwner(continuation, lock, false);
        resumeNext(next);
    }

    public void releaseAll(Task.ContinuationWrapper continuation) {
        continuationRuntime.requireServiceThread();
        Set<CoroutineLock> locks = ownedLocks.get(continuation);
        if (locks == null || locks.isEmpty()) {
            return;
        }
        for (CoroutineLock lock : new ArrayList<>(locks)) {
            resumeNext(releaseOwner(continuation, lock, true));
        }
    }

    private void resumeNext(ReadyContinuation next) {
        if (next == null) {
            return;
        }
        if (next.timerId != 0L) {
            timerScheduler.cancel(next.timerId);
        }
        continuationRuntime.resume(next.continuation, null, Task.Reason.UNLOCK);
    }

    private boolean cancelWait(CoroutineLock.WaitingContinuation waitingContinuation) {
        CoroutineLock lock = waitingContinuation.lock;
        if (!lock.waiters.remove(waitingContinuation)) {
            return false;
        }
        if (lock.owner == null && lock.waiters.isEmpty()) {
            locks.remove(lock.key, lock);
        }
        return true;
    }

    private ReadyContinuation releaseOwner(Task.ContinuationWrapper continuation,
                                           CoroutineLock lock,
                                           boolean releaseAll) {
        if (lock.owner != continuation) {
            throw new IllegalStateException("coroutine lock owner mismatch: " + lock.key.key());
        }

        CoroutineLock.ReleaseStatus status = releaseAll
                ? lock.releaseAll(continuation)
                : lock.release(continuation);
        if (status == CoroutineLock.ReleaseStatus.STILL_OWNED) {
            return null;
        }
        removeOwnedLock(continuation, lock);
        if (status == CoroutineLock.ReleaseStatus.RELEASED) {
            locks.remove(lock.key, lock);
            return null;
        }
        CoroutineLock.WaitingContinuation next = lock.transferToNextOwner();
        addOwnedLock(next.continuation, lock);
        return new ReadyContinuation(next.continuation, next.timerId);
    }

    private void addOwnedLock(Task.ContinuationWrapper continuation, CoroutineLock lock) {
        ownedLocks.computeIfAbsent(continuation, ignored -> new HashSet<>()).add(lock);
    }

    private void removeOwnedLock(Task.ContinuationWrapper continuation, CoroutineLock lock) {
        Set<CoroutineLock> locks = ownedLocks.get(continuation);
        if (locks == null || !locks.remove(lock)) {
            throw new IllegalStateException("owned coroutine lock index mismatch: " + lock.key.key());
        }
        if (locks.isEmpty()) {
            ownedLocks.remove(continuation);
        }
    }

    public String buildDebugDump() {
        Map<CoroutineLock.Key, CoroutineLock> lockSnapshot;
        try {
            lockSnapshot = new HashMap<>(locks);
        } catch (RuntimeException e) {
            return CoroutineLockDebugFormatter.buildDebugDump(
                    CoroutineLockDebugFormatter.snapshot(List.of(), e));
        }
        List<Map.Entry<CoroutineLock.Key, CoroutineLock>> entries = new ArrayList<>(lockSnapshot.entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<CoroutineLock.Key, CoroutineLock> left) -> left.getKey().type().code())
                .thenComparing(left -> String.valueOf(left.getKey().key())));
        List<CoroutineLockDebugFormatter.LockSnapshot> lockSnapshots = new ArrayList<>(entries.size());
        for (Map.Entry<CoroutineLock.Key, CoroutineLock> entry : entries) {
            CoroutineLock.Key key = entry.getKey();
            CoroutineLock lock = entry.getValue();
            List<Task.ContinuationWrapper> waitersSnapshot = new ArrayList<>(lock.waiters.size());
            for (CoroutineLock.WaitingContinuation waiter : lock.waiters) {
                waitersSnapshot.add(waiter.continuation);
            }
            lockSnapshots.add(CoroutineLockDebugFormatter.lockSnapshot(
                    key.type(), key.key(), lock.owner, waitersSnapshot));
        }
        return CoroutineLockDebugFormatter.buildDebugDump(
                CoroutineLockDebugFormatter.snapshot(lockSnapshots, null));
    }

    public void close() {
        List<Long> timerIds = new ArrayList<>();
        for (CoroutineLock lock : locks.values()) {
            for (CoroutineLock.WaitingContinuation waiter : lock.waiters) {
                if (waiter.timerId != 0L) {
                    timerIds.add(waiter.timerId);
                }
            }
        }
        timerScheduler.cancelAll(timerIds);
        closed = true;
        locks.clear();
        ownedLocks.clear();
    }

    private void timeoutWait(LockType type,
                             Object key,
                             int timeoutMillis,
                             CoroutineLock.WaitingContinuation waitingContinuation) {
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
