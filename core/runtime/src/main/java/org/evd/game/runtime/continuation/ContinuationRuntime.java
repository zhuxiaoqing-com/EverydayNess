package org.evd.game.runtime.continuation;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.util.TimerScheduler;
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

    @FunctionalInterface
    public interface WaitTimeoutHandler extends WaitRegistry.WaitTimeoutHandler {
    }

    private final Service service;
    private final ContinuationPool continuationPool;
    private final WaitRegistry waitRegistry;
    private final Consumer<Task.ContinuationWrapper> completionHandler;
    private long conIdAlloc = 1;
    private Task.ContinuationWrapper runningContinuation;
    private final Map<Long, Task.ContinuationWrapper> continuations = new HashMap<>();
    private final ArrayDeque<Task.ContinuationWrapper> readyContinuations = new ArrayDeque<>();
    private long lastDrainDeferredLogNanos;
    private boolean closed;

    public ContinuationRuntime(Service service,
                               TimerScheduler timerScheduler,
                               Consumer<Task.ContinuationWrapper> completionHandler) {
        this.service = service;
        this.completionHandler = completionHandler;
        this.continuationPool = new ContinuationPool(this);
        this.waitRegistry = new WaitRegistry(timerScheduler, this::queue);
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

    public int drain(String phase, int maxCount) {
        if (maxCount <= 0) {
            return 0;
        }

        int scheduled = Math.min(maxCount, readyContinuations.size());
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

    public long registerWait(long timeoutMillis, long now, WaitTimeoutHandler timeoutHandler) {
        return registerWait(timeoutMillis, now, WaitType.GENERIC, timeoutHandler,
                new ContinuationDebugInfo.WaitTimeoutDebugInfo(timeoutMillis));
    }

    public long registerWait(long timeoutMillis,
                             long now,
                             WaitType type,
                             WaitTimeoutHandler timeoutHandler,
                             ContinuationDebugInfo.DebugInfo waitDebugInfo) {
        Task.ContinuationWrapper continuation = requireRunning();
        return waitRegistry.register(continuation, timeoutMillis, now, type, timeoutHandler, waitDebugInfo);
    }

    public boolean cancelWait(long waitId) {
        return waitRegistry.cancel(waitId);
    }

    public boolean completeWait(long waitId, Object result, Task.Reason reason) {
        return waitRegistry.complete(waitId, result, reason);
    }

    public boolean failWait(long waitId, RuntimeException failure, Task.Reason reason) {
        return waitRegistry.fail(waitId, failure, reason);
    }

    public WaitRegistry waitRegistry() {
        return waitRegistry;
    }

    public boolean bindWaitTransport(long waitId, long channelId) {
        return waitRegistry.bindTransport(waitId, channelId);
    }

    public int failWaitsForConnection(long channelId) {
        return waitRegistry.failForConnection(channelId);
    }

    private void logDrainBudgetExceeded(String phase) {
        LogCore.core.error(
                "continuation drain execution budget exceeded: service={}, phase={}",
                service.getId(), phase);
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
        return ContinuationRuntimeDebugFormatter.section(
                "  等待中协程:\n",
                waitRegistry.snapshotContinuations(),
                null);
    }

    private ContinuationRuntimeDebugFormatter.SnapshotSection snapshotHeldContinuations() {
        Map<Long, Task.ContinuationWrapper> heldSnapshot;
        List<Task.ContinuationWrapper> waitSnapshot;
        List<Task.ContinuationWrapper> readySnapshot;
        try {
            heldSnapshot = new HashMap<>(continuations);
            waitSnapshot = waitRegistry.snapshotContinuations();
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
        for (Task.ContinuationWrapper waiting : waitSnapshot) {
            excludedConIds.add(waiting.getConId());
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

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        waitRegistry.close();
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
