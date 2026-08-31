package org.evd.game.runtime;

import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
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
import java.util.Objects;
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
        boolean sent = false;
        try {
            call.setId(waitId);
            send(call);
            sent = true;
        } finally {
            if (!sent) {
                cancelPendingRpc(waitId);
            }
        }
        continuation.markWaiting(debugInfo);
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
        PendingRpcCall pendingRpcCall = new PendingRpcCall(
                continuation, debugInfo, timeoutFailureFactory);
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

    boolean completePendingRpc(CallResult callResult) {
        PendingRpcCall pendingRpcCall = removePendingRpc(callResult);
        if (pendingRpcCall == null) {
            return false;
        }
        service.continuationRuntime().resume(
                pendingRpcCall.continuation,
                callResult.result,
                Task.Reason.RPC);
        return true;
    }

    boolean failPendingRpc(CallResult callResult, RuntimeException failure) {
        PendingRpcCall pendingRpcCall = removePendingRpc(callResult);
        if (pendingRpcCall == null) {
            return false;
        }
        service.continuationRuntime().fail(
                pendingRpcCall.continuation,
                failure,
                Task.Reason.RPC);
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
            service.continuationRuntime().fail(
                    pendingRpcCall.continuation,
                    new RpcTransportException(pendingRpcCall.debugInfo.getTargetCallPoint(), waitId),
                    Task.Reason.RPC);
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

        // 目标节点与当前节点身份一致时，直接在本地投递。
        if (node.isLocalNode(call.to)) {
            call.setOutboundSessionId(0L);
            long waitId = call instanceof CallResult ? 0L : call.getId();
            if (waitId != 0L && !bindPendingRpcSession(waitId, 0L)) {
                throw new RpcTransportException("rpc wait is not bindable: service={}, waitId={}, sessionId=0",
                        serviceId, waitId);
            }
            node.postLocalCall(call);
            return;
        }

        if (call instanceof CallResult callResult && callResult.getSourceSessionId() >= 0L) {
            node.postCallResultOnSource(callResult);
            return;
        }
        CallPoint toNodePoint = call.to == null ? null : call.to.nodePoint();
        Integer toNodeId = toNodePoint == null ? null : toNodePoint.nodeId;
        boolean local = node.isLocalNode(call.to);
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
        CallFrameBufferKey bufferKey = new CallFrameBufferKey(toNodePoint, sessionId);
        CallPulseBuffer buffer = callFrameBuffers.get(bufferKey);
        if (buffer == null) {
            buffer = new CallPulseBuffer(toNodePoint, sessionId);
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

    private PendingRpcCall removePendingRpc(CallResult callResult) {
        if (callResult == null) {
            return null;
        }
        PendingRpcCall pendingRpcCall = pendingRpcCalls.get(callResult.id);
        if (pendingRpcCall == null) {
            return null;
        }
        CallPoint expectedNodePoint = pendingRpcCall.debugInfo.getTargetCallPoint() == null
                ? null : pendingRpcCall.debugInfo.getTargetCallPoint().nodePoint();
        CallPoint sourceNodePoint = callResult.from == null ? null : callResult.from.nodePoint();
        long expectedSessionId = pendingRpcCall.debugInfo.getSessionId();
        if (!Objects.equals(expectedNodePoint, sourceNodePoint)
                || expectedSessionId != callResult.getSourceSessionId()) {
            LogCore.remote.error(
                    "RPC响应来源与等待不匹配，拒绝完成: service={}, waitId={}, expectedNode={}, sourceNode={}, expectedSession={}, sourceSession={}",
                    serviceId,
                    callResult.id,
                    expectedNodePoint,
                    sourceNodePoint,
                    expectedSessionId,
                    callResult.getSourceSessionId());
            return null;
        }
        return removePendingRpc(callResult.id);
    }

    /** 丢弃指定 Session 尚未刷出的消息。 */
    void discard(CallPoint remoteNodePoint, long sessionId) {
        CallFrameBufferKey bufferKey = new CallFrameBufferKey(remoteNodePoint, sessionId);
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
        RuntimeException timeoutFailure;
        try {
            timeoutFailure = pendingRpcCall.timeoutFailureFactory.apply(waitId);
        } catch (VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        } catch (Throwable failure) {
            timeoutFailure = new SysException(
                    failure,
                    "rpc timeout failure factory failed: waitId=" + waitId);
        }
        service.continuationRuntime().fail(
                pendingRpcCall.continuation,
                timeoutFailure,
                Task.Reason.TIMER);
    }

    private PendingRpcCall removePendingRpc(long waitId) {
        PendingRpcCall pendingRpcCall = pendingRpcCalls.remove(waitId);
        if (pendingRpcCall != null && pendingRpcCall.timerId != 0L) {
            timerScheduler.cancel(pendingRpcCall.timerId);
        }
        return pendingRpcCall;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("call transport is closed: service=" + serviceId);
        }
    }

    private record CallFrameBufferKey(CallPoint nodePoint, long sessionId) {
    }
}
