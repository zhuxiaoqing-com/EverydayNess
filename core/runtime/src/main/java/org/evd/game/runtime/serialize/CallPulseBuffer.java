package org.evd.game.runtime.serialize;

import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.misc.BufferPool;
import org.evd.game.runtime.support.exception.RpcTransportException;

import java.util.List;
import java.util.Map;

/**
 *
 * 
 * 请求缓冲
 */
public class CallPulseBuffer implements AutoCloseable{
	/** 目标Node名称 */
	private final String targetNodeId;
	/** 调用所属的 Service，用于结束被丢弃调用关联的 wait。 */
	private final Service service;
	/**
	 * Service 线程顺序刷新各 channel；同一时刻只有一个有效 channel，
	 * 因此复用同一输出流即可，避免为每次刷新反复申请缓冲。
	 */
	private final OutputStream buffer = new OutputStream();

	private long channelId;
	/**
	 * 构造函数
	 * @param targetNodeId 目标 Node
	 */
	public CallPulseBuffer(String targetNodeId, long channelId, Service service) {
		this.targetNodeId = targetNodeId;
		this.channelId = channelId;
		this.service = service;
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
			if (!node.canSendOutboundConnection_nt(targetNodeId, channelId)) {
				org.evd.game.runtime.support.LogCore.remote.warn(
						"出站 channel 已失效，跳过该 channel 缓冲: targetNode={}, channelId={}",
						targetNodeId, channelId);
				return;
			}
			node.flushCall_st(targetNodeId, channelId, buffer.getBuffer(), buffer.getLength());
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
