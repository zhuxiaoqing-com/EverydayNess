package org.evd.game.runtime.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 基于优先队列的精确毫秒定时器实现，保留原 TimerScheduler 的调度语义。
 */
public final class PriorityQueueTimerScheduler implements TimerScheduler {
    private static final class TimerTask {
        private final long id;
        private long deadline;
        private final long interval;
        private final boolean repeated;
        private final Runnable callback;
        private boolean cancelled;

        private TimerTask(long id, long deadline, long interval, boolean repeated, Runnable callback) {
            this.id = id;
            this.deadline = deadline;
            this.interval = interval;
            this.repeated = repeated;
            this.callback = callback;
        }
    }

    private long idAlloc = 1L;
    private final Map<Long, TimerTask> tasks = new HashMap<>();
    private final PriorityQueue<TimerTask> queue = new PriorityQueue<>(
            (left, right) -> Long.compare(left.deadline, right.deadline));
    private final CallbackFailureHandler callbackFailureHandler;
    private boolean closed;

    public PriorityQueueTimerScheduler() {
        this(PriorityQueueTimerScheduler::rethrow);
    }

    public PriorityQueueTimerScheduler(CallbackFailureHandler callbackFailureHandler) {
        this.callbackFailureHandler = Objects.requireNonNull(callbackFailureHandler, "callbackFailureHandler");
    }

    @Override
    public long scheduleDelay(long now, long delayMillis, Runnable callback) {
        return schedule(now + Math.max(delayMillis, 0L), 0L, false, callback);
    }

    @Override
    public long scheduleAt(long deadline, Runnable callback) {
        return schedule(deadline, 0L, false, callback);
    }

    @Override
    public long scheduleRepeated(long now, long intervalMillis, boolean immediate, Runnable callback) {
        long interval = Math.max(intervalMillis, 1L);
        return schedule(immediate ? now : now + interval, interval, true, callback);
    }

    @Override
    public boolean cancel(long timerId) {
        TimerTask task = tasks.remove(timerId);
        if (task == null) {
            return false;
        }
        task.cancelled = true;
        queue.remove(task);
        return true;
    }

    @Override
    public int cancelAll(Collection<Long> timerIds) {
        Objects.requireNonNull(timerIds, "timerIds");
        if (timerIds.isEmpty()) {
            return 0;
        }

        Set<Long> cancelledIds = new HashSet<>();
        for (Long timerId : timerIds) {
            if (timerId == null) {
                continue;
            }
            TimerTask task = tasks.remove(timerId);
            if (task == null) {
                continue;
            }
            task.cancelled = true;
            cancelledIds.add(timerId);
        }
        if (!cancelledIds.isEmpty()) {
            queue.removeIf(task -> cancelledIds.contains(task.id));
        }
        return cancelledIds.size();
    }

    @Override
    public void update(long now) {
        if (closed) {
            return;
        }
        while (!queue.isEmpty()) {
            TimerTask task = queue.peek();
            if (task.deadline > now) {
                return;
            }
            queue.poll();
            if (task.cancelled || !tasks.containsKey(task.id)) {
                continue;
            }

            if (task.repeated) {
                task.deadline += task.interval;
                queue.offer(task);
            } else {
                tasks.remove(task.id);
            }
            runCallback(task);
        }
    }

    @Override
    public void close() {
        closed = true;
        tasks.clear();
        queue.clear();
    }

    private long schedule(long deadline, long interval, boolean repeated, Runnable callback) {
        if (closed) {
            throw new IllegalStateException("timer scheduler is closed");
        }
        Objects.requireNonNull(callback, "callback");
        long timerId = idAlloc++;
        TimerTask task = new TimerTask(timerId, deadline, interval, repeated, callback);
        tasks.put(timerId, task);
        queue.offer(task);
        return timerId;
    }

    private void runCallback(TimerTask task) {
        try {
            task.callback.run();
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError virtualMachineError) {
                throw virtualMachineError;
            }
            callbackFailureHandler.onFailure(task.id, failure);
        }
    }

    private static void rethrow(long timerId, Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException("timer callback failed: timerId=" + timerId, failure);
    }
}
