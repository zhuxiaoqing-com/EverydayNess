package org.evd.game.runtime.actor;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class ActorAddressIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, ActorAddress instance) throws IOException {
		org.evd.game.runtime.call.CallPointIOSerializer.write(out, instance.getCallPoint());
		out.writeLong(instance.getOwnerInstanceId());
		out.writeLong(instance.getMailBoxInstanceId());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, ActorAddress instance) throws IOException {
		org.evd.game.runtime.call.CallPoint callPoint = new org.evd.game.runtime.call.CallPoint();
		org.evd.game.runtime.call.CallPointIOSerializer.read(in, callPoint);
		instance.setCallPoint(callPoint);
		instance.setOwnerInstanceId(in.readLong());
		instance.setMailBoxInstanceId(in.readLong());
	}
}
