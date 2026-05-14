package org.evd.game.ConnService;

import org.evd.game.base.ISerializable;
import org.evd.game.runtime.serialize.OutputStream;
import org.evd.game.runtime.serialize.InputStream;
import java.io.IOException;


/**
*
* 注册序列化和反序列化函数指针
*/
final class SerializerRegister{

	/**
	* 注册
	*/
	static void register(){
		registerWrite();
		registerRead();
		registerReadEnum();
	}
	/**

	/**
	* 注册序列化
	*/
	private static void registerWrite(){
	}
	/**
	* 注册反序列化
	*/
	private static void registerRead(){
	}
	/**
	* 注册反序列化枚举
	*/
	private static void registerReadEnum(){
		InputStream.registerSerializeReadEnumFunc(-441921041, SerializerRegister::TestEnumReadEnum);
	}



	public static Enum<?> TestEnumReadEnum(InputStream in, int ordinal) throws IOException{
		return org.evd.game.ConnService.TestEnum.values()[ordinal];
	}
}