package org.evd.game.runtime;

import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.misc.BufferPool;
import org.evd.game.runtime.serialize.CallPulseBuffer;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.RpcTransportException;

import java.util.HashMap;
import java.util.Map;

final class CallTransport {
    private final Node node;
    private final Service service;
    private final String serviceId;
    /** 每个远端 Node 的每个 Session 各自持有独立缓冲，避免重连后混用旧 Session。 */
    private final Map<CallFrameBufferKey, CallPulseBuffer> callFrameBuffers = new HashMap<>();

    CallTransport(Node node, Service service) {
        this.node = node;
        this.service = service;
        this.serviceId = service.getId();
    }

    /** 在传输入口捕获当前 Session，并将等待绑定到该 Session。 */
    void send(CallBase call) {
        DebugPrint.printSendRpc(null, call);
        if (call == null) {
            throw new RpcTransportException("rpc transport unavailable: null call");
        }

        // NodeId一样 直接原地转发
        if (service.node.getId().equals(call.to.getNodeId())) {
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
        if (waitId != 0L && !service.continuationRuntime().bindWaitTransport(waitId, sessionId)) {
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

    private record CallFrameBufferKey(String nodeId, long sessionId) {
    }
}
