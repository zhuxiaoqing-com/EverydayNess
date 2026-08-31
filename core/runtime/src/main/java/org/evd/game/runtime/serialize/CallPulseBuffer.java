package org.evd.game.runtime.serialize;

import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.Node;

/**
 *
 * 
 * 请求缓冲
 */
public class CallPulseBuffer implements AutoCloseable{
	/** 目标 Node 点位 */
	private final CallPoint targetNodePoint;
	/**
	 * Service 线程顺序刷新各 Session；同一时刻只有一个有效 Session，
	 * 因此复用同一输出流即可，避免为每次刷新反复申请缓冲。
	 */
	private final OutputStream buffer = new OutputStream();

	private final long sessionId;
	/**
	 * 构造函数
	 * @param targetNodePoint 目标 Node
	 */
	public CallPulseBuffer(CallPoint targetNodePoint, long sessionId) {
		this.targetNodePoint = new CallPoint(targetNodePoint);
		this.sessionId = sessionId;
	}
	
	/**
	 * 写入新请求
     */
	public boolean writeCall(CallBase call) {
		return buffer.writeCall(call);
	}

	/**
	 * 刷新缓冲区
	 * @param node 当前 Node
	 */
	public void flush_st(Node node) {
		if (buffer.getLength() == 0) {
			return;
		}

		try {
			if (!node.canSendOutboundSession_nt(targetNodePoint, sessionId)) {
				org.evd.game.runtime.support.LogCore.remote.warn(
					"出站 Session 已失效，跳过该 Session 缓冲: targetNode={}, sessionId={}",
					targetNodePoint, sessionId);
				return;
			}
			node.flushCall_st(targetNodePoint, sessionId, buffer.getBuffer(), buffer.getLength());
		} finally {
			buffer.reset();
		}
	}

	/**
	 * 缓冲区是否有未发送数据
	 * @return
	 */
	public boolean isEmpty() {
		return buffer.getLength() == 0;
	}
	/**
	 * 获取已使用长度
	 * @return
	 */
	public int getLength() {
		return buffer.getLength();
	}
	
	@Override
	public void close() {
		buffer.close();
	}
}
