package org.evd.game.runtime;

import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.call.RpcCallBase;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.misc.BufferPool;
import org.evd.game.runtime.serialize.CallPulseBuffer;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.support.exception.RpcTransportException;
import org.evd.game.runtime.util.TimerScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

public final class CallTransport {
    private static final class PendingRpcCall {
        private final Task.ContinuationWrapper continuation;
        private final ContinuationDebugInfo.RpcWaitDebugInfo debugInfo;
        private final LongFunction<RuntimeException> timeoutFailureFactory;
        private long timerId;

        private PendingRpcCall(Task.ContinuationWrapper continuation,
                               ContinuationDebugInfo.RpcWaitDebugInfo debugInfo,
                               LongFunction<RuntimeException> timeoutFailureFactory) {
            this.continuation = continuation;
            this.debugInfo = debugInfo;
            this.timeoutFailureFactory = timeoutFailureFactory;
        }
    }

    private final Node node;
    private final Service service;
    private final String serviceId;
    private final TimerScheduler timerScheduler;
    /** 每个远端 Node 的每个 Session 各自持有独立缓冲，避免重连后混用旧 Session。 */
    private final Map<CallFrameBufferKey, CallPulseBuffer> callFrameBuffers = new HashMap<>();
    /** RPC 请求 ID 到等待协程的映射，只服务于 RPC 回包、超时和断链。 */
    private final Map<Long, PendingRpcCall> pendingRpcCalls = new HashMap<>();
    private boolean closed;

    CallTransport(Node node, Service service, TimerScheduler timerScheduler) {
        this.node = node;
        this.service = service;
        this.serviceId = service.getId();
        this.timerScheduler = timerScheduler;
    }

    public Object awaitRpcCall(RpcCallBase call,
                               long timeoutMillis,
                               ContinuationDebugInfo.RpcWaitDebugInfo debugInfo,
                               LongFunction<RuntimeException> timeoutFailureFactory) {
        if (!call.isNeedResult()) {
            throw new SysException("rpc await requires needResult=true: service={}, callType={}, target={}, methodKey={}",
                    serviceId, call.getClass().getSimpleName(), call.getTo(), call.getMethodKey());
        }
        Task.ContinuationWrapper continuation = service.continuationRuntime().requireRunning();
        long waitId = registerPendingRpc(continuation, timeoutMillis, debugInfo, timeoutFailureFactory);
        try {
            call.setId(waitId);
            send(call);
        } catch (Exception e) {
            cancelPendingRpc(waitId);
            throw e;
        }
        return continuation.waitResult();
    }

    long registerPendingRpc(Task.ContinuationWrapper continuation,
                            long timeoutMillis,
                            ContinuationDebugInfo.RpcWaitDebugInfo debugInfo,
                            LongFunction<RuntimeException> timeoutFailureFactory) {
        ensureOpen();
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("rpc wait timeout must be positive: service=" + serviceId
                    + ", timeoutMillis=" + timeoutMillis);
        }
        continuation.prepareWait();
        continuation.markWaiting(debugInfo);
        PendingRpcCall pendingRpcCall = new PendingRpcCall(continuation, debugInfo, timeoutFailureFactory);
        pendingRpcCall.timerId = timerScheduler.scheduleDelay(
                service.getWaitBaseTimeInternal(),
                timeoutMillis,
                () -> timeoutPendingRpc(pendingRpcCall));
        pendingRpcCalls.put(pendingRpcCall.timerId, pendingRpcCall);
        return pendingRpcCall.timerId;
    }

    boolean cancelPendingRpc(long waitId) {
        return removePendingRpc(waitId) != null;
    }

    boolean completePendingRpc(long waitId, Object result) {
        PendingRpcCall pendingRpcCall = removePendingRpc(waitId);
        if (pendingRpcCall == null) {
            return false;
        }
        pendingRpcCall.continuation.setResult(result);
        resume(pendingRpcCall.continuation, Task.Reason.RPC);
        return true;
    }

    boolean failPendingRpc(long waitId, RuntimeException failure) {
        PendingRpcCall pendingRpcCall = removePendingRpc(waitId);
        if (pendingRpcCall == null) {
            return false;
        }
        pendingRpcCall.continuation.setFailure(failure);
        resume(pendingRpcCall.continuation, Task.Reason.RPC);
        return true;
    }

    int failPendingRpcForSession(long sessionId) {
        if (sessionId < 0L) {
            return 0;
        }

        int failed = 0;
        for (Map.Entry<Long, PendingRpcCall> entry : new ArrayList<>(pendingRpcCalls.entrySet())) {
            PendingRpcCall pendingRpcCall = entry.getValue();
            if (pendingRpcCall.debugInfo.getSessionId() != sessionId) {
                continue;
            }
            long waitId = entry.getKey();
            if (removePendingRpc(waitId) == null) {
                continue;
            }
            pendingRpcCall.continuation.setFailure(
                    new RpcTransportException(pendingRpcCall.debugInfo.getTargetNodeId(), waitId));
            resume(pendingRpcCall.continuation, Task.Reason.RPC);
            failed++;
        }
        return failed;
    }

    /** 在传输入口捕获当前 Session，并将等待绑定到该 Session。 */
    void send(CallBase call) {
        DebugPrint.printSendRpc(null, call);
        if (call == null) {
            throw new RpcTransportException("rpc transport unavailable: null call");
        }

        // NodeId一样 直接原地转发
        if (service.node.getId().equals(call.to.getNodeId())) {
            call.setOutboundSessionId(0L);
            long waitId = call instanceof CallResult ? 0L : call.getId();
            if (waitId != 0L && !bindPendingRpcSession(waitId, 0L)) {
                throw new RpcTransportException("rpc wait is not bindable: service={}, waitId={}, sessionId=0",
                        serviceId, waitId);
            }
            node.post(() -> node.callHandle_snt(call, null));
            return;
        }

        if (call instanceof CallResult callResult && callResult.getSourceSessionId() >= 0L) {
            if (!node.sendCallResultOnSource(callResult)) {
                LogCore.remote.warn("远程 RPC 结果原 Session 不可写，丢弃结果: localNode={}, remoteNode={}, sessionId={}, waitId={}",
                        node.getId(), callResult.to == null ? null : callResult.to.nodeId,
                        callResult.getSourceSessionId(), callResult.getId());
            }
            return;
        }
        String toNodeId = call.to == null ? null : call.to.nodeId;
        boolean local = node.getId().equals(toNodeId);
        RemoteSession session = local ? null : node.captureRemoteSession(call);
        if (!local && session == null) {
            LogCore.remote.warn("远程Node Service当前不可接收业务RPC，拒绝进入出站缓冲: localNode={}, remoteNode={}, service={}, callType={}",
                    node.getId(), toNodeId, call.to == null ? null : call.to.servId,
                    call.getClass().getSimpleName());
            throw new RpcTransportException("rpc transport unavailable: service={}, toNode={}, toService={}, callType={}",
                    serviceId, toNodeId, call.to == null ? null : call.to.servId,
                    call.getClass().getSimpleName());
        }
        long sessionId = local ? 0L : session.getSessionId();
        call.setOutboundSessionId(sessionId);
        long waitId = call instanceof CallResult ? 0L : call.getId();
        if (waitId != 0L && !bindPendingRpcSession(waitId, sessionId)) {
            throw new RpcTransportException("rpc wait is not bindable: service={}, waitId={}, sessionId={}",
                    serviceId, waitId, sessionId);
        }
        CallFrameBufferKey bufferKey = new CallFrameBufferKey(toNodeId, sessionId);
        CallPulseBuffer buffer = callFrameBuffers.get(bufferKey);
        if (buffer == null) {
            buffer = new CallPulseBuffer(toNodeId, sessionId);
            callFrameBuffers.put(bufferKey, buffer);
        }

        if (!buffer.writeCall(call)) {
            LogCore.core.warn("第一次尝试写入缓冲失败：bufferLen={}, nodeId={}, portId={}, remoteNodeId={} call {}",
                    buffer.getLength(), serviceId, node.getId(), toNodeId, call);
            buffer.flush_st(node);
            if (!buffer.writeCall(call)) {
                LogCore.core.error("第二次尝试写入缓冲失败, call请求最大支持2M：bufferLen={} call {} ", buffer.getLength(), call);
                throw new RpcTransportException(
                        "rpc call serialized payload too large: service={}, toNode={}, toService={}, callType={}, maxBytes={}",
                        serviceId, toNodeId, call.to == null ? null : call.to.servId,
                        call.getClass().getSimpleName(), BufferPool.BUFFER_SIZE);
            }
        }
        return;
    }

    void flush() {
        for (CallPulseBuffer frameCache : callFrameBuffers.values()) {
            try {
                frameCache.flush_st(node);
            } catch (Throwable e) {
                LogCore.core.error("", e);
            }
        }
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Long> timerIds = new ArrayList<>(pendingRpcCalls.size());
        for (PendingRpcCall pendingRpcCall : pendingRpcCalls.values()) {
            if (pendingRpcCall.timerId != 0L) {
                timerIds.add(pendingRpcCall.timerId);
            }
        }
        timerScheduler.cancelAll(timerIds);
        pendingRpcCalls.clear();
        for (CallPulseBuffer frameCache : callFrameBuffers.values()) {
            frameCache.close();
        }
        callFrameBuffers.clear();
    }

    /** 丢弃指定 Session 尚未刷出的消息。 */
    void discard(String remoteNodeId, long sessionId) {
        CallFrameBufferKey bufferKey = new CallFrameBufferKey(remoteNodeId, sessionId);
        CallPulseBuffer buffer = callFrameBuffers.remove(bufferKey);
        if (buffer != null) {
            buffer.close();
        }
    }

    private boolean bindPendingRpcSession(long waitId, long sessionId) {
        PendingRpcCall pendingRpcCall = pendingRpcCalls.get(waitId);
        if (pendingRpcCall == null || sessionId < 0L) {
            return false;
        }
        pendingRpcCall.debugInfo.setSessionId(sessionId);
        return true;
    }

    private void timeoutPendingRpc(PendingRpcCall pendingRpcCall) {
        long waitId = pendingRpcCall.timerId;
        if (removePendingRpc(waitId) == null) {
            return;
        }
        try {
            pendingRpcCall.continuation.setFailure(pendingRpcCall.timeoutFailureFactory.apply(waitId));
        } catch (VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        } catch (Throwable failure) {
            pendingRpcCall.continuation.setFailure(new SysException(
                    failure,
                    "rpc timeout failure factory failed: waitId=" + waitId));
        }
        resume(pendingRpcCall.continuation, Task.Reason.TIMER);
    }

    private PendingRpcCall removePendingRpc(long waitId) {
        PendingRpcCall pendingRpcCall = pendingRpcCalls.remove(waitId);
        if (pendingRpcCall != null && pendingRpcCall.timerId != 0L) {
            timerScheduler.cancel(pendingRpcCall.timerId);
        }
        return pendingRpcCall;
    }

    private void resume(Task.ContinuationWrapper continuation, Task.Reason reason) {
        service.continuationRuntime().queue(continuation, reason);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("call transport is closed: service=" + serviceId);
        }
    }

    private record CallFrameBufferKey(String nodeId, long sessionId) {
    }
}
