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
    /** 每个远端 Node 的每个物理 channel 各自持有独立缓冲，避免重连后混用旧 channel。 */
    private final Map<CallFrameBufferKey, CallPulseBuffer> callFrameBuffers = new HashMap<>();

    CallTransport(Node node, Service service) {
        this.node = node;
        this.service = service;
        this.serviceId = service.getId();
    }

    /** 在传输入口捕获当前 channel，并将等待绑定到该 channel。 */
    void send(CallBase call) {
        DebugPrint.printSendRpc(null, call);
        if (call == null) {
            throw new RpcTransportException("rpc transport unavailable: null call");
        }
        String toNodeId = call.to == null ? null : call.to.nodeId;
        long channelId = node.captureChannelId(call);
        if (channelId < 0L) {
            LogCore.remote.warn("远程Node Service当前不可接收业务RPC，拒绝进入出站缓冲: localNode={}, remoteNode={}, service={}, callType={}",
                    node.getId(), toNodeId, call.to == null ? null : call.to.servId,
                    call.getClass().getSimpleName());
            throw new RpcTransportException("rpc transport unavailable: service={}, toNode={}, toService={}, callType={}",
                    serviceId, toNodeId, call.to == null ? null : call.to.servId,
                    call.getClass().getSimpleName());
        }
        call.setOutboundChannelId(channelId);
        long waitId = call instanceof CallResult ? 0L : call.getId();
        if (waitId != 0L && !service.continuationRuntime().bindWaitTransport(waitId, channelId)) {
            throw new RpcTransportException("rpc wait is not bindable: service={}, waitId={}, channelId={}",
                    serviceId, waitId, channelId);
        }
        CallFrameBufferKey bufferKey = new CallFrameBufferKey(toNodeId, channelId);
        CallPulseBuffer buffer = callFrameBuffers.get(bufferKey);
        if (buffer == null) {
            buffer = new CallPulseBuffer(toNodeId, channelId, service);
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

    /** 丢弃指定物理 channel 尚未刷出的消息。 */
    void discard(String remoteNodeId, long channelId) {
        CallFrameBufferKey bufferKey = new CallFrameBufferKey(remoteNodeId, channelId);
        CallPulseBuffer buffer = callFrameBuffers.remove(bufferKey);
        if (buffer != null) {
            buffer.close();
        }
    }

    private record CallFrameBufferKey(String nodeId, long channelId) {
    }
}
