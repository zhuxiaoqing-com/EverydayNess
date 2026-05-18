package org.evd.game.runtime.call;

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;


public final class CallResultIOSerializer{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, CallResult instance) throws IOException {
		org.evd.game.runtime.call.CallBaseIOSerializer.write(out, instance);
		out.writeBoolean(instance.isSuccess());
		out.writeInt(instance.getErrorCode());
		out.writeString(instance.getErrorMessage());
		out.write(instance.getResult());
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, CallResult instance) throws IOException {
		org.evd.game.runtime.call.CallBaseIOSerializer.read(in, instance);
		instance.setSuccess(in.readBoolean());
		instance.setErrorCode(in.readInt());
		instance.setErrorMessage(in.readString());
		instance.setResult(in.read());
	}
}