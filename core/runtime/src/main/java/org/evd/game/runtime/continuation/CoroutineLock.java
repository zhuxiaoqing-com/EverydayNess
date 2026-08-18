package org.evd.game.runtime.continuation;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * 单个 LockKey 对应的锁状态和队列操作。
 */
final class CoroutineLock {
    enum AcquireResult {
        ACQUIRED,   // 首次获取或同一 continuation 重入获取成功
        BUSY,       // 当前由其他 continuation 持有，需要进入等待队列
        REENTRANT   // 同一 continuation 再次获取，已递增 holdCount
    }

    enum ReleaseStatus {
        STILL_OWNED,  // 只减少重入计数，当前 continuation 仍持有锁
        RELEASED,     // 最后一次释放，锁当前没有新的 owner
        TRANSFERRED   // 最后一次释放，锁已准备交接给等待者
    }

    static final class Key {
        private final LockType type;
        private final Object key;

        Key(LockType type, Object key) {
            this.type = type;
            this.key = key;
        }

        LockType type() {
            return type;
        }

        Object key() {
            return key;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return type == that.type && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, key);
        }
    }

    static final class WaitingContinuation {
        final Task.ContinuationWrapper continuation;
        final CoroutineLock lock;
        final long waitId;
        long timerId;

        WaitingContinuation(Task.ContinuationWrapper continuation,
                            CoroutineLock lock,
                            long waitId) {
            this.continuation = continuation;
            this.lock = lock;
            this.waitId = waitId;
        }
    }

    final Key key;
    final ArrayDeque<WaitingContinuation> waiters = new ArrayDeque<>();
    Task.ContinuationWrapper owner;
    int holdCount;

    CoroutineLock(Key key) {
        this.key = key;
    }

    AcquireResult acquire(Task.ContinuationWrapper continuation) {
        if (owner == null) {
            owner = continuation;
            holdCount = 1;
            return AcquireResult.ACQUIRED;
        }
        if (owner == continuation) {
            ++holdCount;
            return AcquireResult.REENTRANT;
        }
        return AcquireResult.BUSY;
    }

    WaitingContinuation enqueue(Task.ContinuationWrapper continuation, long waitId) {
        if (owner == null) {
            throw new IllegalStateException("coroutine lock waiter must have owner first: " + key.key());
        }
        if (owner == continuation) {
            throw new IllegalStateException("coroutine lock waiter cannot equal owner: " + key.key());
        }
        WaitingContinuation waitingContinuation = new WaitingContinuation(continuation, this, waitId);
        waiters.addLast(waitingContinuation);
        return waitingContinuation;
    }

    ReleaseStatus release(Task.ContinuationWrapper continuation) {
        if (owner != continuation) {
            throw new IllegalStateException("coroutine lock owner mismatch: " + key.key());
        }
        if (holdCount <= 0) {
            throw new IllegalStateException("coroutine lock hold count is invalid: " + key.key());
        }
        if (holdCount > 1) {
            --holdCount;
            return ReleaseStatus.STILL_OWNED;
        }
        holdCount = 0;
        owner = null;
        return waiters.isEmpty() ? ReleaseStatus.RELEASED : ReleaseStatus.TRANSFERRED;
    }

    ReleaseStatus releaseAll(Task.ContinuationWrapper continuation) {
        if (owner != continuation) {
            throw new IllegalStateException("coroutine lock owner mismatch: " + key.key());
        }
        holdCount = 1;
        return release(continuation);
    }

    WaitingContinuation transferToNextOwner() {
        if (owner != null || holdCount != 0 || waiters.isEmpty()) {
            throw new IllegalStateException("coroutine lock cannot transfer owner: " + key.key());
        }
        WaitingContinuation next = waiters.pollFirst();
        owner = next.continuation;
        holdCount = 1;
        return next;
    }
}
