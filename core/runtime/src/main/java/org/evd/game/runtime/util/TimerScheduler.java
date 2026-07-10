package org.evd.game.runtime.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public final class TimerScheduler {
    @FunctionalInterface
    public interface CallbackFailureHandler {
        void onFailure(long timerId, Throwable failure);
    }

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

    private long idAlloc = 1;
    private final Map<Long, TimerTask> tasks = new HashMap<>();
    private final PriorityQueue<TimerTask> queue = new PriorityQueue<>((left, right) -> Long.compare(left.deadline, right.deadline));
    private final CallbackFailureHandler callbackFailureHandler;
    private boolean closed;

    public TimerScheduler() {
        this((timerId, failure) -> rethrow(failure));
    }

    public TimerScheduler(CallbackFailureHandler callbackFailureHandler) {
        this.callbackFailureHandler = Objects.requireNonNull(callbackFailureHandler, "callbackFailureHandler");
    }

    public long scheduleDelay(long now, long delayMillis, Runnable callback) {
        long deadline = now + Math.max(delayMillis, 0L);
        return schedule(deadline, 0L, false, callback);
    }

    public long scheduleAt(long deadline, Runnable callback) {
        return schedule(deadline, 0L, false, callback);
    }

    public long scheduleRepeated(long now, long intervalMillis, boolean immediate, Runnable callback) {
        long interval = Math.max(intervalMillis, 1L);
        long deadline = immediate ? now : now + interval;
        return schedule(deadline, interval, true, callback);
    }

    public boolean cancel(long id) {
        TimerTask task = tasks.remove(id);
        if (task == null) {
            return false;
        }
        task.cancelled = true;
        queue.remove(task);
        return true;
    }

    public int cancelAll(Collection<Long> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return 0;
        }

        Set<Long> cancelledIds = new HashSet<>();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            TimerTask task = tasks.remove(id);
            if (task == null) {
                continue;
            }
            task.cancelled = true;
            cancelledIds.add(id);
        }
        if (!cancelledIds.isEmpty()) {
            queue.removeIf(task -> cancelledIds.contains(task.id));
        }
        return cancelledIds.size();
    }

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
            if (task.cancelled) {
                continue;
            }
            if (!tasks.containsKey(task.id)) {
                continue;
            }

            if (task.repeated) {
                task.deadline += task.interval;
                queue.offer(task);
            } else {
                tasks.remove(task.id);
            }

            try {
                task.callback.run();
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError virtualMachineError) {
                    throw virtualMachineError;
                }
                callbackFailureHandler.onFailure(task.id, failure);
            }
        }
    }

    private long schedule(long deadline, long interval, boolean repeated, Runnable callback) {
        if (closed) {
            throw new IllegalStateException("timer scheduler is closed");
        }
        Objects.requireNonNull(callback, "callback");
        long id = idAlloc++;
        TimerTask task = new TimerTask(id, deadline, interval, repeated, callback);
        tasks.put(id, task);
        queue.offer(task);
        return id;
    }

    public void close() {
        closed = true;
        tasks.clear();
        queue.clear();
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(failure);
    }
}
