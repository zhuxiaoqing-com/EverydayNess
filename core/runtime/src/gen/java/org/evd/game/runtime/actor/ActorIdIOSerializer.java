package org.evd.game.runtime.actor;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class ActorIdIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, ActorId instance) throws IOException {
		out.writeInt(instance.getTypeCode());
		out.writeLong(instance.getUniqueId());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, ActorId instance) throws IOException {
		instance.setTypeCode(in.readInt());
		instance.setUniqueId(in.readLong());
	}
}
