package org.evd.game.runtime.call;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.support.LogCore;

/**
 * 发送给远程的call
 */
public class RemoteCall {
    /** 远程的nodeId */
    String remoteNodeId;
    /** call数据 */
    byte[] buffer;

    public RemoteCall(String remoteNodeId, byte[] data) {
        this.remoteNodeId = remoteNodeId;
        this.buffer = data;
    }

	public String getRemoteNodeId() {
		return remoteNodeId;
	}

	public byte[] getBuffer() {
		return buffer;
	}

	@Override
	public String toString() {
		Call call = null;
		if (buffer != null) {
			try {
				InputStream in = new InputStream(buffer, 0, buffer.length);
				call = in.read();
			} catch (Exception e) {
				LogCore.remote.error("RemoteCall 转字符串时解析buffer失败: remoteNodeId={}", remoteNodeId, e);
			}
		}
		return "RemoteCall [remoteNodeId=" + remoteNodeId + ", call=" + call + "]";
	}
    
}
