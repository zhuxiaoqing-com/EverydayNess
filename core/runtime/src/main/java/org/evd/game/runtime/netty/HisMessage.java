package org.evd.game.runtime.netty;

public class HisMessage {
	private int cmd;
	private Object body;
	private long currTime;

	public HisMessage(int cmd, Object body, long currTime) {
		this.cmd = cmd;
		this.body = body;
		this.currTime = currTime;
	}

	public int getCmd() {
		return cmd;
	}

	public Object getBody() {
		return body;
	}

	public long getCurrTime() {
		return currTime;
	}
}
