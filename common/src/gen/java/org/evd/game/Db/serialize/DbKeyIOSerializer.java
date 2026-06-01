package org.evd.game.Db.serialize;

import org.evd.game.runtime.Db.serialize.DbKey;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class DbKeyIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, DbKey instance) throws IOException {
		instance.beforeWrite(out);
		instance.writeTo(out);
		instance.afterWrite(out);
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, DbKey instance) throws IOException {
		instance.beforeRead(in);
		instance.readFrom(in);
		instance.afterRead(in);
	}
}
