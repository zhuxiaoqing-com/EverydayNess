package org.evd.game.Db.serialize;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class MysqlReqIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, MysqlReq instance) throws IOException {
		out.writeString(instance.getSql());
		out.writeString(instance.getTableName());
		out.writeList(instance.getTablFieldList());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, MysqlReq instance) throws IOException {
		instance.setSql(in.readString());
		instance.setTableName(in.readString());
		instance.setTablFieldList(in.readList());
	}
}
