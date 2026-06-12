package org.evd.game.runtime;

import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.serialize.CallPulseBuffer;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

final class CallTransport {
    private final Node node;
    private final String serviceId;
    private final Map<String, CallPulseBuffer> callFrameBuffers = new HashMap<>();

    CallTransport(Node node, String serviceId) {
        this.node = node;
        this.serviceId = serviceId;
    }

    boolean send(CallBase call) {
        String toNodeId = call.to.nodeId;
        if (node.getId().equals(toNodeId)) {
            node.callHandle_snt(call);
            return true;
        }

        CallPulseBuffer buffer = callFrameBuffers.get(toNodeId);
        if (buffer == null) {
            buffer = new CallPulseBuffer(toNodeId);
            callFrameBuffers.put(toNodeId, buffer);
        }

        if (!buffer.writeCall(call)) {
            LogCore.core.warn("第一次尝试写入缓冲失败：bufferLen={}, nodeId={}, portId={}, remoteNodeId={}",
                    buffer.getLength(), serviceId, node.getId(), toNodeId);
            buffer.flush_st(node);
            if (!buffer.writeCall(call)) {
                LogCore.core.error("第二次尝试写入缓冲失败, call请求最大支持2M：bufferLen={}", buffer.getLength());
                return false;
            }
        }
        return true;
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
        for (Map.Entry<String, CallPulseBuffer> entry : callFrameBuffers.entrySet()) {
            entry.getValue().close();
        }
        callFrameBuffers.clear();
    }
}
