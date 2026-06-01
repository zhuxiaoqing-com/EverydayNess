package org.evd.game.Db.serialize;

import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.MysqlReq;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class DBReqIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, DBReq instance) throws IOException {
		out.write(instance.getDbOpType());
		org.evd.game.Db.serialize.MysqlReqIOSerializer.write(out, instance.getMysqlReq());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, DBReq instance) throws IOException {
		instance.setDbOpType(in.read());
		MysqlReq mysqlReq = new MysqlReq();
		org.evd.game.Db.serialize.MysqlReqIOSerializer.read(in, mysqlReq);
		instance.setMysqlReq(mysqlReq);
	}
}
