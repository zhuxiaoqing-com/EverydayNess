package org.evd.game.common.serializeBean;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class ConnInfoIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, ConnInfo instance) throws IOException {
		org.evd.game.common.serializeBean.ConnInfoBaseIOSerializer.write(out, instance);
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, ConnInfo instance) throws IOException {
		org.evd.game.common.serializeBean.ConnInfoBaseIOSerializer.read(in, instance);
	}
}
