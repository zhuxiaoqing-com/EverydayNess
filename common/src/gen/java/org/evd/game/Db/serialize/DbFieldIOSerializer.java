package org.evd.game.Db.serialize;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class DbFieldIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, DbField instance) throws IOException {
		out.writeString(instance.getName());
		org.evd.game.Db.serialize.DbValueIOSerializer.write(out, instance.getValue());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, DbField instance) throws IOException {
		instance.setName(in.readString());
		org.evd.game.Db.serialize.DbValue value = new org.evd.game.Db.serialize.DbValue();
		org.evd.game.Db.serialize.DbValueIOSerializer.read(in, value);
		instance.setValue(value);
	}
}
