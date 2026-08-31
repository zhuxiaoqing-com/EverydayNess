package org.evd.game.runtime.call;

import org.evd.game.runtime.serializeBean.NodeFrameChunk;
import org.evd.game.runtime.support.LogCore;

/**
 * 发送给远程的call
 */
public class RemoteCall {
    /** 远程 Node 的完整点位。 */
    CallPoint remoteNodePoint;
    /** call数据 */
    NodeFrameChunk packet;
    /** 入队时绑定的 RemoteSession 标识。 */
    long expectedSessionId;

    public RemoteCall(CallPoint remoteNodePoint, long expectedSessionId, NodeFrameChunk data) {
        this.remoteNodePoint = new CallPoint(remoteNodePoint);
        this.expectedSessionId = expectedSessionId;
        this.packet = data;
    }

	public int getRemoteNodeId() {
		return remoteNodePoint.nodeId;
	}

	public CallPoint getRemoteNodePoint() {
		return new CallPoint(remoteNodePoint);
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
				LogCore.remote.error("RemoteCall 转字符串时解析buffer失败: remoteNodePoint={}", remoteNodePoint, e);
			}
		}
		return "RemoteCall [remoteNodePoint=" + remoteNodePoint + ", call=" + call + "]";
	}
    
}
