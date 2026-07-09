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

    public RemoteCall(String remoteNodeId, NodeFrameChunk data) {
        this.remoteNodeId = remoteNodeId;
        this.packet = data;
    }

	public String getRemoteNodeId() {
		return remoteNodeId;
	}

	public NodeFrameChunk getPacket() {
		return packet;
	}

	@Override
	public String toString() {
		Call call = null;
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
