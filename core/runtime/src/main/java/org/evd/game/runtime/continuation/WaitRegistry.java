package org.evd.game.runtime.continuation;

import org.evd.game.runtime.util.TimerScheduler;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.support.exception.RpcTransportException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 统一管理协程等待、超时和等待句柄的生命周期。
 *
 * <p>它只负责等待状态，不负责决定协程何时运行。协程恢复由调用方注入，
 * 这样 RPC、锁、sleep 等不同等待来源可以共享同一套完成语义。</p>
 */
public final class WaitRegistry {
    @FunctionalInterface
    public interface WaitTimeoutHandler {
        void onTimeout(Task.ContinuationWrapper continuation, long waitId);
    }

    private static final class WaitContext {
        private final Task.ContinuationWrapper continuation;
        private final long timerId;
        private final WaitTimeoutHandler timeoutHandler;
        private final WaitType type;
        private final ContinuationDebugInfo.DebugInfo debugInfo;

        private WaitContext(Task.ContinuationWrapper continuation, long timerId,
                            WaitTimeoutHandler timeoutHandler, WaitType type,
                            ContinuationDebugInfo.DebugInfo debugInfo) {
            this.continuation = continuation;
            this.timerId = timerId;
            this.timeoutHandler = timeoutHandler;
            this.type = type;
            this.debugInfo = debugInfo;
        }
    }

    private final TimerScheduler timerScheduler;
    private final BiConsumer<Task.ContinuationWrapper, Task.Reason> resumer;
    private final Map<Long, WaitContext> waitContexts = new HashMap<>();
    private long waitIdAlloc = 1L;
    private boolean closed;

    public WaitRegistry(TimerScheduler timerScheduler,
                        BiConsumer<Task.ContinuationWrapper, Task.Reason> resumer) {
        this.timerScheduler = timerScheduler;
        this.resumer = resumer;
    }

    public long register(Task.ContinuationWrapper continuation,
                         long timeoutMillis,
                         long now,
                         WaitType type,
                         WaitTimeoutHandler timeoutHandler,
                         ContinuationDebugInfo.DebugInfo waitDebugInfo) {
        ensureOpen();
        continuation.prepareWait();
        continuation.markWaiting(waitDebugInfo);
        long waitId = waitIdAlloc++;
        long timerId = timeoutMillis > 0
                ? timerScheduler.scheduleDelay(now, timeoutMillis, () -> onWaitTimeout(waitId))
                : 0L;
        waitContexts.put(waitId, new WaitContext(
                continuation,
                timerId,
                timeoutHandler,
                type,
                waitDebugInfo));
        return waitId;
    }

    public boolean cancel(long waitId) {
        return remove(waitId) != null;
    }

    public boolean isPending(long waitId) {
        return waitContexts.containsKey(waitId);
    }

    public boolean complete(long waitId, Object result, Task.Reason reason) {
        WaitContext waitContext = remove(waitId);
        if (waitContext == null) {
            return false;
        }
        waitContext.continuation.setResult(result);
        resumer.accept(waitContext.continuation, reason);
        return true;
    }

    public boolean fail(long waitId, RuntimeException failure, Task.Reason reason) {
        WaitContext waitContext = remove(waitId);
        if (waitContext == null) {
            return false;
        }
        waitContext.continuation.setFailure(failure);
        resumer.accept(waitContext.continuation, reason);
        return true;
    }

    public boolean bindTransport(long waitId, long sessionId) {
        WaitContext context = waitContexts.get(waitId);
        if (context == null || context.type != WaitType.RPC || sessionId < 0L
                || !(context.debugInfo instanceof ContinuationDebugInfo.RpcWaitDebugInfo rpcWaitDebugInfo)) {
            return false;
        }
        rpcWaitDebugInfo.setSessionId(sessionId);
        return true;
    }

    public int failForSession(long sessionId) {
        if (sessionId < 0L) {
            return 0;
        }

        int failed = 0;
        for (Map.Entry<Long, WaitContext> entry : new ArrayList<>(waitContexts.entrySet())) {
            WaitContext waitContext = entry.getValue();
            if (waitContext.type != WaitType.RPC
                    || !(waitContext.debugInfo instanceof ContinuationDebugInfo.RpcWaitDebugInfo rpcWaitDebugInfo)
                    || sessionId != rpcWaitDebugInfo.getSessionId()) {
                continue;
            }

            long waitId = entry.getKey();
            if (remove(waitId) == null) {
                continue;
            }
            waitContext.continuation.setFailure(new RpcTransportException(rpcWaitDebugInfo.getTargetNodeId(), waitId));
            resumer.accept(waitContext.continuation, Task.Reason.RPC);
            failed++;
        }
        return failed;
    }

    public List<Task.ContinuationWrapper> snapshotContinuations() {
        List<Map.Entry<Long, WaitContext>> entries = new ArrayList<>(waitContexts.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        List<Task.ContinuationWrapper> continuations = new ArrayList<>(entries.size());
        for (Map.Entry<Long, WaitContext> entry : entries) {
            continuations.add(entry.getValue().continuation);
        }
        return continuations;
    }

    /**
     * 关闭等待注册表并取消所有等待定时器。
     * 等待中的协程属于正在关闭的运行时，不再重新入队。
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Long> timerIds = new ArrayList<>(waitContexts.size());
        for (WaitContext waitContext : waitContexts.values()) {
            if (waitContext.timerId != 0L) {
                timerIds.add(waitContext.timerId);
            }
        }
        timerScheduler.cancelAll(timerIds);
        waitContexts.clear();
    }

    private void onWaitTimeout(long waitId) {
        WaitContext waitContext = remove(waitId);
        if (waitContext == null) {
            return;
        }
        completeByTimeout(waitContext, waitId);
    }

    private void completeByTimeout(WaitContext waitContext, long waitId) {
        try {
        waitContext.timeoutHandler.onTimeout(waitContext.continuation, waitId);
        } catch (VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        } catch (Throwable failure) {
            waitContext.continuation.setFailure(new SysException(
                    failure,
                    "wait timeout handler failed: waitId=" + waitId));
        }
        resumer.accept(waitContext.continuation, Task.Reason.TIMER);
    }

    private WaitContext remove(long waitId) {
        WaitContext waitContext = waitContexts.remove(waitId);
        if (waitContext != null && waitContext.timerId != 0L) {
            timerScheduler.cancel(waitContext.timerId);
        }
        return waitContext;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("wait registry is closed");
        }
    }
}
