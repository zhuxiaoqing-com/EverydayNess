package org.evd.game.runtime.call;

import org.evd.game.runtime.serializeBean.NodeFrameChunk;
import org.evd.game.runtime.support.LogCore;

/**
 * 发送给远程的call
 */
public class RemoteCall {
    /** 远程的nodeId */
    String remoteNodeId;
    /** call数据 */
    NodeFrameChunk packet;
    /** 入队时绑定的 RemoteSession 标识。 */
    long expectedSessionId;

    public RemoteCall(String remoteNodeId, long expectedSessionId, NodeFrameChunk data) {
        this.remoteNodeId = remoteNodeId;
        this.expectedSessionId = expectedSessionId;
        this.packet = data;
    }

	public String getRemoteNodeId() {
		return remoteNodeId;
	}

	public NodeFrameChunk getPacket() {
		return packet;
	}

    public long getExpectedSessionId() {
        return expectedSessionId;
    }

	@Override
	public String toString() {
		CallBase call = null;
		if (packet != null) {
			try {
				call = packet.newPayloadInputStream().read();
			} catch (Exception e) {
				LogCore.remote.error("RemoteCall 转字符串时解析buffer失败: remoteNodeId={}", remoteNodeId, e);
			}
		}
		return "RemoteCall [remoteNodeId=" + remoteNodeId + ", call=" + call + "]";
	}
    
}
