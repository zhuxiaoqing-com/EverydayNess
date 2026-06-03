package org.evd.game.common;

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
		OutputStream.registerSerializeWriteFunc(-1806497995, SerializerRegister::org_evd_game_common_serializeBean_ConnInfoBaseWrite);
		OutputStream.registerSerializeWriteFunc(784569860, SerializerRegister::org_evd_game_common_serializeBean_CoInfoWrite);
		OutputStream.registerSerializeWriteFunc(-884191676, SerializerRegister::org_evd_game_common_serializeBean_ConnInfoWrite);
		OutputStream.registerSerializeWriteFunc(2005701922, SerializerRegister::org_evd_game_common_TestSerWrite);
	}
	/**
	* 注册反序列化
	*/
	private static void registerRead(){
		InputStream.registerSerializeReadFunc(-1806497995, SerializerRegister::org_evd_game_common_serializeBean_ConnInfoBaseRead);
		InputStream.registerSerializeReadFunc(784569860, SerializerRegister::org_evd_game_common_serializeBean_CoInfoRead);
		InputStream.registerSerializeReadFunc(-884191676, SerializerRegister::org_evd_game_common_serializeBean_ConnInfoRead);
		InputStream.registerSerializeReadFunc(2005701922, SerializerRegister::org_evd_game_common_TestSerRead);
	}
	/**
	* 注册反序列化枚举
	*/
	private static void registerReadEnum(){
	}

	public static void org_evd_game_common_serializeBean_ConnInfoBaseWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.common.serializeBean.ConnInfoBaseIOSerializer.write(out, (org.evd.game.common.serializeBean.ConnInfoBase)ser);
	}
	public static void org_evd_game_common_serializeBean_CoInfoWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.common.serializeBean.CoInfoIOSerializer.write(out, (org.evd.game.common.serializeBean.CoInfo)ser);
	}
	public static void org_evd_game_common_serializeBean_ConnInfoWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.common.serializeBean.ConnInfoIOSerializer.write(out, (org.evd.game.common.serializeBean.ConnInfo)ser);
	}
	public static void org_evd_game_common_TestSerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.common.TestSerIOSerializer.write(out, (org.evd.game.common.TestSer)ser);
	}

	public static ISerializable org_evd_game_common_serializeBean_ConnInfoBaseRead(InputStream in) throws IOException{
		org.evd.game.common.serializeBean.ConnInfoBase connInfoBase = new org.evd.game.common.serializeBean.ConnInfoBase();
		org.evd.game.common.serializeBean.ConnInfoBaseIOSerializer.read(in, connInfoBase);
		return connInfoBase;
	}
	public static ISerializable org_evd_game_common_serializeBean_CoInfoRead(InputStream in) throws IOException{
		org.evd.game.common.serializeBean.CoInfo coInfo = new org.evd.game.common.serializeBean.CoInfo();
		org.evd.game.common.serializeBean.CoInfoIOSerializer.read(in, coInfo);
		return coInfo;
	}
	public static ISerializable org_evd_game_common_serializeBean_ConnInfoRead(InputStream in) throws IOException{
		org.evd.game.common.serializeBean.ConnInfo connInfo = new org.evd.game.common.serializeBean.ConnInfo();
		org.evd.game.common.serializeBean.ConnInfoIOSerializer.read(in, connInfo);
		return connInfo;
	}
	public static ISerializable org_evd_game_common_TestSerRead(InputStream in) throws IOException{
		org.evd.game.common.TestSer testSer = new org.evd.game.common.TestSer();
		org.evd.game.common.TestSerIOSerializer.read(in, testSer);
		return testSer;
	}

}
