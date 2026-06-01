package org.evd.game.Db.serialize;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class DBRspIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, DBRsp instance) throws IOException {
		out.writeBoolean(instance.isSuccess());
		out.writeString(instance.getExceptionMessage());
		org.evd.game.Db.serialize.MysqlRspIOSerializer.write(out, instance.getMysqlRsp());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, DBRsp instance) throws IOException {
		instance.setSuccess(in.readBoolean());
		instance.setExceptionMessage(in.readString());
		org.evd.game.Db.serialize.MysqlRsp mysqlRsp = new org.evd.game.Db.serialize.MysqlRsp();
		org.evd.game.Db.serialize.MysqlRspIOSerializer.read(in, mysqlRsp);
		instance.setMysqlRsp(mysqlRsp);
	}
}
