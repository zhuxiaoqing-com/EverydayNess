package org.evd.game.runtime;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class ClientSessionRefIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, ClientSessionRef instance) throws IOException {
		org.evd.game.runtime.call.CallPointIOSerializer.write(out, instance.getGate());
		out.writeLong(instance.getSessionId());
		out.writeLong(instance.getRouteKey());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, ClientSessionRef instance) throws IOException {
		org.evd.game.runtime.call.CallPoint gate = new org.evd.game.runtime.call.CallPoint();
		org.evd.game.runtime.call.CallPointIOSerializer.read(in, gate);
		instance.setGate(gate);
		instance.setSessionId(in.readLong());
		instance.setRouteKey(in.readLong());
	}
}