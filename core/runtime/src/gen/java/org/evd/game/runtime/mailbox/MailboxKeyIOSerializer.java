package org.evd.game.runtime.mailbox;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class MailboxKeyIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, MailboxKey instance) throws IOException {
		out.writeInt(instance.getKindCode());
		out.writeLong(instance.getId());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, MailboxKey instance) throws IOException {
		instance.setKindCode(in.readInt());
		instance.setId(in.readLong());
	}
}