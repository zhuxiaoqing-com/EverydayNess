package org.evd.game.runtime;

import org.evd.game.runtime.mailbox.MailboxKey;
import org.evd.game.runtime.support.SysException;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class ContinuationRuntime {
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
    private boolean drainingReadyContinuations;

    ContinuationRuntime(Service service, TimerScheduler timerScheduler) {
        this.service = service;
        this.timerScheduler = timerScheduler;
        this.continuationPool = new ContinuationPool(service);
    }

    public Task.ContinuationWrapper create(Runnable task, MailboxKey mailboxKey) {
        Task.ContinuationWrapper context = continuationPool.apply();
        context.bindTask(task, nextConId(), mailboxKey);
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

    public void queue(Task.ContinuationWrapper continuation) {
        readyContinuations.addLast(continuation);
        if (!drainingReadyContinuations) {
            drain();
        }
    }

    public void drain() {
        if (drainingReadyContinuations) {
            return;
        }
        drainingReadyContinuations = true;
        try {
            Task.ContinuationWrapper continuation;
            while ((continuation = readyContinuations.pollFirst()) != null) {
                runImmediate(continuation);
            }
        } finally {
            drainingReadyContinuations = false;
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
        queue(waitContext.continuation);
    }

    private long nextConId() {
        return conIdAlloc++;
    }

    private long nextWaitId() {
        return waitIdAlloc++;
    }
}
