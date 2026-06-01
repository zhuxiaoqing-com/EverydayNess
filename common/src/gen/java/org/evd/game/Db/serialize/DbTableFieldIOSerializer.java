package org.evd.game.Db.serialize;

import org.evd.game.runtime.Db.serialize.DbTableField;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class DbTableFieldIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, DbTableField instance) throws IOException {
		out.writeList(instance.getValueList());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, DbTableField instance) throws IOException {
		instance.setValueList(in.readList());
	}
}
