package org.evd.game.runtime.util;

import org.agrona.DeadlineTimerWheel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Agrona {@link DeadlineTimerWheel} 的毫秒级时间轮调度器。
 *
 * <p>DeadlineTimerWheel 的槽位 ID 会复用，本实现使用独立的单调 timerId
 * 对外暴露，保证 RPC 等调用方可以在调度器生命周期内稳定关联一次定时任务。</p>
 */
public final class DeadlineTimerWheelScheduler implements TimerScheduler {
    public static final long DEFAULT_TICK_RESOLUTION_MILLIS = 4L;
    public static final int DEFAULT_TICKS_PER_WHEEL = 512;
    public static final int DEFAULT_INITIAL_TICK_ALLOCATION = 16;

    private static final long NO_WHEEL_TIMER = -1L;

    private static final class TimerTask {
        private final long timerId;
        private long deadline;
        private final long interval;
        private final boolean repeated;
        private final Runnable callback;
        private long wheelTimerId = NO_WHEEL_TIMER;

        private TimerTask(long timerId, long deadline, long interval, boolean repeated, Runnable callback) {
            this.timerId = timerId;
            this.deadline = deadline;
            this.interval = interval;
            this.repeated = repeated;
            this.callback = callback;
        }
    }

    private final CallbackFailureHandler callbackFailureHandler;
    private final long tickResolutionMillis;
    private final int ticksPerWheel;
    private final int initialTickAllocation;
    private final Map<Long, TimerTask> tasks = new HashMap<>();
    private final Map<Long, TimerTask> tasksByWheelTimerId = new HashMap<>();
    private long timerIdAlloc = 1L;
    private DeadlineTimerWheel timerWheel;
    private boolean closed;

    public DeadlineTimerWheelScheduler() {
        this(DeadlineTimerWheelScheduler::rethrow);
    }

    public DeadlineTimerWheelScheduler(CallbackFailureHandler callbackFailureHandler) {
        this(callbackFailureHandler,
                DEFAULT_TICK_RESOLUTION_MILLIS,
                DEFAULT_TICKS_PER_WHEEL,
                DEFAULT_INITIAL_TICK_ALLOCATION);
    }

    public DeadlineTimerWheelScheduler(CallbackFailureHandler callbackFailureHandler,
                                long tickResolutionMillis,
                                int ticksPerWheel,
                                int initialTickAllocation) {
        this.callbackFailureHandler = Objects.requireNonNull(callbackFailureHandler, "callbackFailureHandler");
        this.tickResolutionMillis = tickResolutionMillis;
        this.ticksPerWheel = ticksPerWheel;
        this.initialTickAllocation = initialTickAllocation;
    }

    @Override
    public long scheduleDelay(long now, long delayMillis, Runnable callback) {
        return schedule(now + Math.max(delayMillis, 0L), 0L, false, callback, now);
    }

    @Override
    public long scheduleAt(long deadline, Runnable callback) {
        return schedule(deadline, 0L, false, callback, deadline);
    }

    @Override
    public long scheduleRepeated(long now, long intervalMillis, boolean immediate, Runnable callback) {
        long interval = Math.max(intervalMillis, 1L);
        return schedule(immediate ? now : now + interval, interval, true, callback, now);
    }

    @Override
    public boolean cancel(long timerId) {
        TimerTask task = tasks.remove(timerId);
        if (task == null) {
            return false;
        }
        cancelWheelTimer(task);
        return true;
    }

    @Override
    public int cancelAll(Collection<Long> timerIds) {
        Objects.requireNonNull(timerIds, "timerIds");
        int cancelled = 0;
        for (Long timerId : timerIds) {
            if (timerId != null && cancel(timerId)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    @Override
    public void update(long now) {
        if (closed || timerWheel == null) {
            return;
        }
        if (tasks.isEmpty()) {
            timerWheel.resetStartTime(now);
            return;
        }

        while (timerWheel.currentTickTime() <= now) {
            timerWheel.poll(now, this::onTimerExpiry, Integer.MAX_VALUE);
        }
        // DeadlineTimerWheel 每次 poll 至多推进一个 tick；补查推进后的当前 tick，
        // 保证本次 update 已跨入的 tick 内到期任务不会延后到下一帧。
        timerWheel.poll(now, this::onTimerExpiry, Integer.MAX_VALUE);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        tasks.clear();
        tasksByWheelTimerId.clear();
        if (timerWheel != null) {
            timerWheel.clear();
        }
    }

    private long schedule(long deadline,
                          long interval,
                          boolean repeated,
                          Runnable callback,
                          long wheelStartTime) {
        ensureOpen();
        Objects.requireNonNull(callback, "callback");
        ensureTimerWheel(wheelStartTime);
        long timerId = timerIdAlloc++;
        TimerTask task = new TimerTask(timerId, deadline, interval, repeated, callback);
        tasks.put(timerId, task);
        scheduleWheelTimer(task);
        return timerId;
    }

    private boolean onTimerExpiry(TimeUnit timeUnit, long now, long wheelTimerId) {
        TimerTask task = tasksByWheelTimerId.remove(wheelTimerId);
        if (task == null || tasks.get(task.timerId) != task) {
            return true;
        }
        task.wheelTimerId = NO_WHEEL_TIMER;
        if (!task.repeated) {
            tasks.remove(task.timerId);
            runCallback(task);
            return true;
        }

        do {
            task.deadline += task.interval;
            if (task.deadline > now) {
                scheduleWheelTimer(task);
            }
            runCallback(task);
            if (tasks.get(task.timerId) != task || task.deadline > now) {
                return true;
            }
        } while (true);
    }

    private void scheduleWheelTimer(TimerTask task) {
        long wheelTimerId = timerWheel.scheduleTimer(task.deadline);
        task.wheelTimerId = wheelTimerId;
        tasksByWheelTimerId.put(wheelTimerId, task);
    }

    private void cancelWheelTimer(TimerTask task) {
        if (task.wheelTimerId == NO_WHEEL_TIMER) {
            return;
        }
        tasksByWheelTimerId.remove(task.wheelTimerId);
        timerWheel.cancelTimer(task.wheelTimerId);
        task.wheelTimerId = NO_WHEEL_TIMER;
    }

    private void ensureTimerWheel(long startTime) {
        if (timerWheel == null) {
            timerWheel = new DeadlineTimerWheel(
                    TimeUnit.MILLISECONDS,
                    startTime,
                    tickResolutionMillis,
                    ticksPerWheel,
                    initialTickAllocation);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("timer scheduler is closed");
        }
    }

    private void runCallback(TimerTask task) {
        try {
            task.callback.run();
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError virtualMachineError) {
                throw virtualMachineError;
            }
            callbackFailureHandler.onFailure(task.timerId, failure);
        }
    }

    private static void rethrow(long timerId, Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException("timer callback failed: timerId=" + timerId, failure);
    }
}
