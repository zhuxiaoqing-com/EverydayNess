package org.evd.game.runtime.continuation;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.util.TimerScheduler;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.SysException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContinuationRuntime {
    private static final int DRAIN_LOG_THRESHOLD = 100;
    private static final int DRAIN_DEFER_THRESHOLD = 1000;

    @FunctionalInterface
    public interface WaitTimeoutHandler {
        void onTimeout(Task.ContinuationWrapper continuation, long waitId);
    }

    private static final class WaitContext {
        private final Task.ContinuationWrapper continuation;
        private final long timerId;
        private final WaitTimeoutHandler timeoutHandler;

        private WaitContext(Task.ContinuationWrapper continuation,
                            long timerId,
                            WaitTimeoutHandler timeoutHandler) {
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

    public ContinuationRuntime(Service service, TimerScheduler timerScheduler) {
        this.service = service;
        this.timerScheduler = timerScheduler;
        this.continuationPool = new ContinuationPool(service);
    }


    public void createAndEnterQueue(Runnable task, ActorId actorId, Task.Reason queueReason, ContinuationDebugInfo.DebugInfo debugInfo) {
        Task.ContinuationWrapper context = continuationPool.apply();
        context.bindTask(task, nextConId(), actorId);
        context.bindDebugInfo(debugInfo);
        queue(context, queueReason);
    }


    public void createAndRun(Runnable task, ActorId actorId) {
        Task.ContinuationWrapper context = continuationPool.apply();
        context.bindTask(task, nextConId(), actorId);
        runImmediate(context);
    }

    public Task.ContinuationWrapper create(Runnable task, ActorId actorId) {
        Task.ContinuationWrapper context = continuationPool.apply();
        context.bindTask(task, nextConId(), actorId);
        return context;
    }

    public void runImmediate(Task.ContinuationWrapper continuation) {
        runningContinuation = continuation;
        continuation.markRunning();
        try {
            continuation.runVirtual();
        } finally {
            continuation.markExecutionPaused();
            runningContinuation = null;
        }
    }

    public void hold(Task.ContinuationWrapper continuation) {
        continuations.put(continuation.getConId(), continuation);
    }

    public void unhold(Task.ContinuationWrapper continuation, Runnable afterUnhold) {
        continuations.remove(continuation.getConId());
        afterUnhold.run();
        continuation.markCompleted();
        continuationPool.recycle(continuation);
    }

    public void queue(Task.ContinuationWrapper continuation, Task.Reason queueReason) {
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
        return registerWait(timeoutMillis, now, timeoutHandler, new ContinuationDebugInfo.WaitTimeoutDebugInfo(timeoutMillis));
    }

    public long registerWait(long timeoutMillis,
                             long now,
                             WaitTimeoutHandler timeoutHandler,
                             ContinuationDebugInfo.DebugInfo waitDebugInfo) {
        long waitId = nextWaitId();
        Task.ContinuationWrapper continuation = requireRunning();
        continuation.prepareWait();
        continuation.markWaiting(waitDebugInfo);
        long timerId = timeoutMillis > 0
                ? timerScheduler.scheduleDelay(now, timeoutMillis, () -> onWaitTimeout(waitId))
                : 0L;
        waitContexts.put(waitId, new WaitContext(
                continuation,
                timerId,
                timeoutHandler));
        return waitId;
    }

    /**
     * 按 waitId 把“正在等结果的协程”从等待表里取出来，并顺手取消它的超时定时器。
     */
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
        queue(waitContext.continuation, Task.Reason.TIMER);
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
            ContinuationDebugInfo.DebugInfo debugInfo = pending.getDebugInfo();
            if (debugInfo == null) {
                continue;
            }
            Task.Reason queueReason = pending.getQueueReason();
            String key = debugInfo + " | " + (queueReason == null ? "unknown" : queueReason.name());
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

    public String buildDebugDump() {
        return ContinuationRuntimeDebugFormatter.buildDebugDump(
                runningContinuation,
                snapshotReadyContinuations(),
                snapshotWaitingContinuations(),
                snapshotHeldContinuations());
    }

    private ContinuationRuntimeDebugFormatter.SnapshotSection snapshotReadyContinuations() {
        try {
            return ContinuationRuntimeDebugFormatter.section(
                    "  就绪协程队列:\n",
                    new ArrayList<>(readyContinuations),
                    null);
        } catch (RuntimeException e) {
            return ContinuationRuntimeDebugFormatter.section(
                    "  就绪协程队列:\n",
                    List.of(),
                    e);
        }
    }

    private ContinuationRuntimeDebugFormatter.SnapshotSection snapshotWaitingContinuations() {
        Map<Long, WaitContext> waitSnapshot;
        try {
            waitSnapshot = new HashMap<>(waitContexts);
        } catch (RuntimeException e) {
            return ContinuationRuntimeDebugFormatter.section(
                    "  等待中协程:\n",
                    List.of(),
                    e);
        }
        List<Map.Entry<Long, WaitContext>> entries = new ArrayList<>(waitSnapshot.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        List<Task.ContinuationWrapper> continuations = new ArrayList<>(entries.size());
        for (Map.Entry<Long, WaitContext> entry : entries) {
            WaitContext waitContext = entry.getValue();
            continuations.add(waitContext.continuation);
        }
        return ContinuationRuntimeDebugFormatter.section(
                "  等待中协程:\n",
                continuations,
                null);
    }

    private ContinuationRuntimeDebugFormatter.SnapshotSection snapshotHeldContinuations() {
        Map<Long, Task.ContinuationWrapper> heldSnapshot;
        Map<Long, WaitContext> waitSnapshot;
        List<Task.ContinuationWrapper> readySnapshot;
        try {
            heldSnapshot = new HashMap<>(continuations);
            waitSnapshot = new HashMap<>(waitContexts);
            readySnapshot = new ArrayList<>(readyContinuations);
        } catch (RuntimeException e) {
            return ContinuationRuntimeDebugFormatter.section(
                    "  已持有但未入队/未等待协程:\n",
                    List.of(),
                    e);
        }

        Set<Long> excludedConIds = new HashSet<>();
        Task.ContinuationWrapper current = runningContinuation;
        if (current != null) {
            excludedConIds.add(current.getConId());
        }
        for (Task.ContinuationWrapper ready : readySnapshot) {
            excludedConIds.add(ready.getConId());
        }
        for (WaitContext waitContext : waitSnapshot.values()) {
            excludedConIds.add(waitContext.continuation.getConId());
        }

        List<Task.ContinuationWrapper> active = new ArrayList<>();
        for (Task.ContinuationWrapper continuation : heldSnapshot.values()) {
            if (excludedConIds.contains(continuation.getConId())) {
                continue;
            }
            active.add(continuation);
        }
        active.sort((left, right) -> Long.compare(left.getConId(), right.getConId()));
        return ContinuationRuntimeDebugFormatter.section(
                "  已持有但未入队/未等待协程:\n",
                active,
                null);
    }

    private long nextConId() {
        return conIdAlloc++;
    }

    private long nextWaitId() {
        return waitIdAlloc++;
    }
}
