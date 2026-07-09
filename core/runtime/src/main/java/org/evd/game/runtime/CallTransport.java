package org.evd.game.runtime;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.config.GlobalConfig;
import org.evd.game.runtime.serialize.CallPulseBuffer;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

@Slf4j
final class CallTransport {
    private final Node node;
    private final String serviceId;
    private final Map<String, CallPulseBuffer> callFrameBuffers = new HashMap<>();

    CallTransport(Node node, String serviceId) {
        this.node = node;
        this.serviceId = serviceId;
    }

    boolean send(CallBase call) {
        RemoteNode.sendRpcLog(call, null);
        String toNodeId = call.to.nodeId;
        // 这里投放是得话 是不序列化，直接发送，里面还有一个判断，是进行序列化的，目前这里先注释掉
       /* if (node.getId().equals(toNodeId)) {
             node.callHandle_snt(call);
             return true;
         }*/

        CallPulseBuffer buffer = callFrameBuffers.get(toNodeId);
        if (buffer == null) {
            buffer = new CallPulseBuffer(toNodeId);
            callFrameBuffers.put(toNodeId, buffer);
        }

        if (!buffer.writeCall(call)) {
            LogCore.core.warn("第一次尝试写入缓冲失败：bufferLen={}, nodeId={}, portId={}, remoteNodeId={} call {}",
                    buffer.getLength(), serviceId, node.getId(), toNodeId, call);
            buffer.flush_st(node);
            if (!buffer.writeCall(call)) {
                LogCore.core.error("第二次尝试写入缓冲失败, call请求最大支持2M：bufferLen={} call {} ", buffer.getLength(), call);
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
