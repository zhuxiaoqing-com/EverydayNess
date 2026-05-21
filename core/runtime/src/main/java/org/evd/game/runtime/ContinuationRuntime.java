package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.SysException;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class ContinuationRuntime {
    private static final int DRAIN_LOG_THRESHOLD = 100;
    private static final int DRAIN_DEFER_THRESHOLD = 1000;

    @FunctionalInterface
    interface WaitTimeoutHandler {
        void onTimeout(Task.ContinuationWrapper continuation, long waitId);
    }

    private static final class WaitContext {
        private final Task.ContinuationWrapper continuation;
        private final long timerId;
        private final WaitTimeoutHandler timeoutHandler;

        private WaitContext(Task.ContinuationWrapper continuation, long timerId, WaitTimeoutHandler timeoutHandler) {
            this.continuation = continuation;
            this.timerId = timerId;
            this.timeoutHandler = timeoutHandler;
        }
    }

    private final Service service;
    private final TimerScheduler timerScheduler;
    private final ContinuationPool continuationPool;
    private long conIdAlloc = 1;
    private long waitIdAlloc = 1;
    private Task.ContinuationWrapper runningContinuation;
    private final Map<Long, Task.ContinuationWrapper> continuations = new HashMap<>();
    private final Map<Long, WaitContext> waitContexts = new HashMap<>();
    private final ArrayDeque<Task.ContinuationWrapper> readyContinuations = new ArrayDeque<>();

    ContinuationRuntime(Service service, TimerScheduler timerScheduler) {
        this.service = service;
        this.timerScheduler = timerScheduler;
        this.continuationPool = new ContinuationPool(service);
    }

    public Task.ContinuationWrapper create(Runnable task, ActorId actorId) {
        Task.ContinuationWrapper context = continuationPool.apply();
        context.bindTask(task, nextConId(), actorId);
        return context;
    }

    public void runImmediate(Task.ContinuationWrapper continuation) {
        runningContinuation = continuation;
        try {
            continuation.runVirtual();
        } finally {
            runningContinuation = null;
        }
    }

    public void hold(Task.ContinuationWrapper continuation) {
        continuations.put(continuation.getConId(), continuation);
    }

    public void unhold(Task.ContinuationWrapper continuation, Runnable afterUnhold) {
        continuations.remove(continuation.getConId());
        afterUnhold.run();
        continuationPool.recycle(continuation);
    }

    public void queue(Task.ContinuationWrapper continuation, String queueReason) {
        continuation.markQueued(queueReason);
        readyContinuations.addLast(continuation);
    }

    public void drain(String phase) {
        int resumed = 0;
        boolean logged = false;
        Task.ContinuationWrapper continuation;
        while ((continuation = readyContinuations.pollFirst()) != null) {
            if (resumed >= DRAIN_DEFER_THRESHOLD) {
                readyContinuations.addFirst(continuation);
                logDrainState("continuation drain deferred to next tick", phase, resumed);
                return;
            }
            if (!logged && resumed >= DRAIN_LOG_THRESHOLD) {
                logged = true;
                logDrainState("continuation drain threshold exceeded", phase, resumed);
            }
            resumed++;
            runImmediate(continuation);
        }
    }

    public Task.ContinuationWrapper requireRunning() {
        Task.ContinuationWrapper continuation = runningContinuation;
        if (continuation == null) {
            throw new SysException("continuation wait must run inside continuation: service={}", service.getId());
        }
        return continuation;
    }

    public long registerWait(long timeoutMillis, long now, WaitTimeoutHandler timeoutHandler) {
        long waitId = nextWaitId();
        Task.ContinuationWrapper continuation = requireRunning();
        continuation.prepareWait();
        long timerId = timeoutMillis > 0
                ? timerScheduler.scheduleDelay(now, timeoutMillis, () -> onWaitTimeout(waitId))
                : 0L;
        waitContexts.put(waitId, new WaitContext(continuation, timerId, timeoutHandler));
        return waitId;
    }

    public Task.ContinuationWrapper takeWaitContinuation(long waitId) {
        WaitContext waitContext = waitContexts.remove(waitId);
        if (waitContext != null && waitContext.timerId != 0) {
            timerScheduler.cancel(waitContext.timerId);
        }
        return waitContext == null ? null : waitContext.continuation;
    }

    private void onWaitTimeout(long waitId) {
        WaitContext waitContext = waitContexts.remove(waitId);
        if (waitContext == null) {
            return;
        }
        waitContext.timeoutHandler.onTimeout(waitContext.continuation, waitId);
        queue(waitContext.continuation, "timer");
    }

    private void logDrainState(String title, String phase, int resumed) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append(title)
                .append(": service=")
                .append(service.getId())
                .append(", phase=").append(phase)
                .append(", resumed=").append(resumed)
                .append(", pending=").append(readyContinuations.size())
                .append('\n');

        Map<String, Integer> pendingDebugCounts = new LinkedHashMap<>();
        for (Task.ContinuationWrapper pending : readyContinuations) {
            Task.DebugInfo debugInfo = pending.getDebugInfo();
            if (debugInfo == null) {
                continue;
            }
            String queueReason = pending.getQueueReason();
            String key = debugInfo + " | " + (queueReason == null ? "unknown" : queueReason);
            pendingDebugCounts.merge(key, 1, Integer::sum);
        }
        sb.append("pending rpc continuations:\n");
        if (pendingDebugCounts.isEmpty()) {
            sb.append("  none\n");
        } else {
            for (Map.Entry<String, Integer> entry : pendingDebugCounts.entrySet()) {
                sb
                        .append(entry.getKey())
                        .append(", ")
                        .append("  count=").append(entry.getValue())
                        .append('\n');
            }
        }

        LogCore.core.error(sb.toString());
    }

    private long nextConId() {
        return conIdAlloc++;
    }

    private long nextWaitId() {
        return waitIdAlloc++;
    }
}
