package org.evd.game.runtime.Db.serialize;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class ObjectValueIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, ObjectValue instance) throws IOException {
		out.write(instance.getType());
		out.writeLong(instance.getLongValue());
		out.writeInt(instance.getIntValue());
		out.writeString(instance.getStringValue());
		out.writeByteArray(instance.getBytesValue());
		out.writeBoolean(instance.isBooleanValue());
		out.writeDouble(instance.getDoubleValue());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, ObjectValue instance) throws IOException {
		instance.setType(in.read());
		instance.setLongValue(in.readLong());
		instance.setIntValue(in.readInt());
		instance.setStringValue(in.readString());
		instance.setBytesValue(in.readByteArray());
		instance.setBooleanValue(in.readBoolean());
		instance.setDoubleValue(in.readDouble());
	}
}
