package org.evd.game.runtime.continuation;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.FrameQueue;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ContinuationRuntime implements ContinuationHost {
    private static final long DRAIN_DEFER_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final Service service;
    private final ContinuationPool continuationPool;
    private final Consumer<Task.ContinuationWrapper> completionHandler;
    private long conIdAlloc = 1;
    private Task.ContinuationWrapper runningContinuation;
    private final Map<Long, Task.ContinuationWrapper> continuations = new HashMap<>();
    private final FrameQueue<Task.ContinuationWrapper> readyContinuations =
            new FrameQueue<>(new ArrayDeque<>());
    private long lastDrainDeferredLogNanos;
    private boolean closed;

    private record DebugDumpSections(
            ContinuationRuntimeDebugFormatter.SnapshotSection ready,
            ContinuationRuntimeDebugFormatter.SnapshotSection waiting,
            ContinuationRuntimeDebugFormatter.SnapshotSection held) {
    }

    public ContinuationRuntime(Service service,
                               Consumer<Task.ContinuationWrapper> completionHandler) {
        this.service = service;
        this.completionHandler = completionHandler;
        this.continuationPool = new ContinuationPool(this);
    }


    public void createAndEnterQueue(Runnable task, ActorId actorId, Task.Reason queueReason, ContinuationDebugInfo.DebugInfo debugInfo) {
        ensureOpen();
        Task.ContinuationWrapper context = continuationPool.apply();
        context.bindTask(task, nextConId(), actorId);
        context.bindDebugInfo(debugInfo);
        queue(context, queueReason);
    }


    public void createAndRun(Runnable task, ActorId actorId) {
        ensureOpen();
        Task.ContinuationWrapper context = continuationPool.apply();
        context.bindTask(task, nextConId(), actorId);
        runImmediate(context);
    }

    public Task.ContinuationWrapper create(Runnable task, ActorId actorId) {
        ensureOpen();
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

    @Override
    public void unhold(Task.ContinuationWrapper continuation) {
        continuations.remove(continuation.getConId());
        try {
            completionHandler.accept(continuation);
        } finally {
            continuation.markCompleted();
            continuationPool.recycle(continuation);
        }
    }

    @Override
    public jdk.internal.vm.ContinuationScope getScope() {
        return service.getScope();
    }

    public void queue(Task.ContinuationWrapper continuation, Task.Reason queueReason) {
        ensureOpen();
        continuation.markQueued(queueReason);
        readyContinuations.addLast(continuation);
    }

    /** 当前等待恢复、尚未运行的 continuation 数量；等待中的 continuation 不包含在内。 */
    public int readySize() {
        return readyContinuations.size();
    }

    public int drain(String phase) {
        int scheduled = readyContinuations.getFrameProcessNum();
        int resumed = 0;
        Task.ContinuationWrapper continuation;
        while (resumed < scheduled && (continuation = readyContinuations.pollFirst()) != null) {
            resumed++;
            long conId = continuation.getConId();
            ActorId actorId = continuation.getActorId();
            ContinuationDebugInfo.DebugInfo debugInfo = continuation.getDebugInfo();
            Task.Reason queueReason = continuation.getQueueReason();
            try {
                runImmediate(continuation);
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError virtualMachineError) {
                    throw virtualMachineError;
                }
                LogCore.core.error(
                        "service coroutine execution failed: service={}, phase={}, conId={}, actorId={}, queueReason={}, debugInfo={}",
                        service.getId(), phase, conId, actorId, queueReason, debugInfo, e);
            }
        }

        if (!readyContinuations.isEmpty()) {
            long now = System.nanoTime();
            if (lastDrainDeferredLogNanos == 0L
                    || now - lastDrainDeferredLogNanos >= DRAIN_DEFER_LOG_INTERVAL_NANOS) {
                lastDrainDeferredLogNanos = now;
                logDrainBudgetExceeded(phase);
            }
        }
        return resumed;
    }

    public Task.ContinuationWrapper requireRunning() {
        Task.ContinuationWrapper continuation = runningContinuation;
        if (continuation == null) {
            throw new SysException("continuation wait must run inside continuation: service={}", service.getId());
        }
        return continuation;
    }

    private void logDrainBudgetExceeded(String phase) {
        LogCore.core.error(
                "continuation drain execution budget exceeded: service={}, phase={}",
                service.getId(), phase);
    }

    public String buildDebugDump() {
        Task.ContinuationWrapper runningSnapshot = runningContinuation;
        DebugDumpSections sections = snapshotDebugDumpSections(runningSnapshot);
        return ContinuationRuntimeDebugFormatter.buildDebugDump(
                runningSnapshot,
                sections.ready(),
                sections.waiting(),
                sections.held());
    }

    private DebugDumpSections snapshotDebugDumpSections(Task.ContinuationWrapper runningSnapshot) {
        Map<Long, Task.ContinuationWrapper> heldSnapshot;
        List<Task.ContinuationWrapper> waitingSnapshot;
        try {
            heldSnapshot = new HashMap<>(continuations);
            waitingSnapshot = snapshotWaitingContinuations(heldSnapshot.values());
        } catch (RuntimeException e) {
            return new DebugDumpSections(
                    snapshotReadyContinuations(),
                    failedSection("  等待中协程:\n", e),
                    failedSection("  已持有但未入队/未等待协程:\n", e));
        }

        List<Task.ContinuationWrapper> readySnapshot;
        try {
            readySnapshot = readyContinuations.snapshot();
        } catch (RuntimeException e) {
            return new DebugDumpSections(
                    failedSection("  就绪协程队列:\n", e),
                    ContinuationRuntimeDebugFormatter.section(
                            "  等待中协程:\n",
                            waitingSnapshot,
                            null),
                    failedSection("  已持有但未入队/未等待协程:\n", e));
        }

        Set<Long> excludedConIds = new HashSet<>();
        if (runningSnapshot != null && heldSnapshot.containsKey(runningSnapshot.getConId())) {
            excludedConIds.add(runningSnapshot.getConId());
        }
        for (Task.ContinuationWrapper ready : readySnapshot) {
            excludedConIds.add(ready.getConId());
        }
        for (Task.ContinuationWrapper waiting : waitingSnapshot) {
            excludedConIds.add(waiting.getConId());
        }

        List<Task.ContinuationWrapper> active = new ArrayList<>();
        for (Task.ContinuationWrapper continuation : heldSnapshot.values()) {
            if (!excludedConIds.contains(continuation.getConId())) {
                active.add(continuation);
            }
        }
        active.sort((left, right) -> Long.compare(left.getConId(), right.getConId()));
        return new DebugDumpSections(
                ContinuationRuntimeDebugFormatter.section(
                        "  就绪协程队列:\n",
                        readySnapshot,
                        null),
                ContinuationRuntimeDebugFormatter.section(
                    "  等待中协程:\n",
                    waitingSnapshot,
                    null),
                ContinuationRuntimeDebugFormatter.section(
                        "  已持有但未入队/未等待协程:\n",
                        active,
                        null));
    }

    private long nextConId() {
        return conIdAlloc++;
    }

    private ContinuationRuntimeDebugFormatter.SnapshotSection snapshotReadyContinuations() {
        try {
            return ContinuationRuntimeDebugFormatter.section(
                    "  就绪协程队列:\n",
                    readyContinuations.snapshot(),
                    null);
        } catch (RuntimeException e) {
            return failedSection("  就绪协程队列:\n", e);
        }
    }

    private static ContinuationRuntimeDebugFormatter.SnapshotSection failedSection(
            String title,
            RuntimeException failure) {
        return ContinuationRuntimeDebugFormatter.section(title, List.of(), failure);
    }

    private static List<Task.ContinuationWrapper> snapshotWaitingContinuations(
            Iterable<Task.ContinuationWrapper> continuations) {
        List<Task.ContinuationWrapper> waiting = new ArrayList<>();
        for (Task.ContinuationWrapper continuation : continuations) {
            if (continuation.getDebugState() == Task.DebugState.WAITING) {
                waiting.add(continuation);
            }
        }
        waiting.sort((left, right) -> Long.compare(left.getConId(), right.getConId()));
        return waiting;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        readyContinuations.clear();
        continuations.clear();
        continuationPool.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("continuation runtime is closed: service=" + service.getId());
        }
    }
}
