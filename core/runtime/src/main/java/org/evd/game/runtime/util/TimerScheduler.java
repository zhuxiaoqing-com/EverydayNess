package org.evd.game.runtime.util;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class TimerScheduler {
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
        return true;
    }

    public void update(long now) {
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

            task.callback.run();
        }
    }

    private long schedule(long deadline, long interval, boolean repeated, Runnable callback) {
        long id = idAlloc++;
        TimerTask task = new TimerTask(id, deadline, interval, repeated, callback);
        tasks.put(id, task);
        queue.offer(task);
        return id;
    }
}
