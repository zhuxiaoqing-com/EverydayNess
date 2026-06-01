package org.evd.game.Db.serialize;

import org.evd.game.runtime.Db.serialize.MysqlRsp;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class MysqlRspIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, MysqlRsp instance) throws IOException {
		out.writeList(instance.getTablFieldList());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, MysqlRsp instance) throws IOException {
		instance.setTablFieldList(in.readList());
	}
}
