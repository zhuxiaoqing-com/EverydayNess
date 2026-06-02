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
		OutputStream.registerSerializeWriteFunc(2005701922, SerializerRegister::org_evd_game_common_TestSerWrite);
		OutputStream.registerSerializeWriteFunc(1965326063, SerializerRegister::org_evd_game_serializeBean_CoInfoWrite);
		OutputStream.registerSerializeWriteFunc(-48846737, SerializerRegister::org_evd_game_serializeBean_ConnInfoWrite);
		OutputStream.registerSerializeWriteFunc(-943828000, SerializerRegister::org_evd_game_serializeBean_ConnInfoBaseWrite);
	}
	/**
	* 注册反序列化
	*/
	private static void registerRead(){
		InputStream.registerSerializeReadFunc(2005701922, SerializerRegister::org_evd_game_common_TestSerRead);
		InputStream.registerSerializeReadFunc(1965326063, SerializerRegister::org_evd_game_serializeBean_CoInfoRead);
		InputStream.registerSerializeReadFunc(-48846737, SerializerRegister::org_evd_game_serializeBean_ConnInfoRead);
		InputStream.registerSerializeReadFunc(-943828000, SerializerRegister::org_evd_game_serializeBean_ConnInfoBaseRead);
	}
	/**
	* 注册反序列化枚举
	*/
	private static void registerReadEnum(){
	}

	public static void org_evd_game_common_TestSerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.common.TestSerIOSerializer.write(out, (org.evd.game.common.TestSer)ser);
	}
	public static void org_evd_game_serializeBean_CoInfoWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.serializeBean.CoInfoIOSerializer.write(out, (org.evd.game.serializeBean.CoInfo)ser);
	}
	public static void org_evd_game_serializeBean_ConnInfoWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.serializeBean.ConnInfoIOSerializer.write(out, (org.evd.game.serializeBean.ConnInfo)ser);
	}
	public static void org_evd_game_serializeBean_ConnInfoBaseWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.serializeBean.ConnInfoBaseIOSerializer.write(out, (org.evd.game.serializeBean.ConnInfoBase)ser);
	}

	public static ISerializable org_evd_game_common_TestSerRead(InputStream in) throws IOException{
		org.evd.game.common.TestSer testSer = new org.evd.game.common.TestSer();
		org.evd.game.common.TestSerIOSerializer.read(in, testSer);
		return testSer;
	}
	public static ISerializable org_evd_game_serializeBean_CoInfoRead(InputStream in) throws IOException{
		org.evd.game.serializeBean.CoInfo coInfo = new org.evd.game.serializeBean.CoInfo();
		org.evd.game.serializeBean.CoInfoIOSerializer.read(in, coInfo);
		return coInfo;
	}
	public static ISerializable org_evd_game_serializeBean_ConnInfoRead(InputStream in) throws IOException{
		org.evd.game.serializeBean.ConnInfo connInfo = new org.evd.game.serializeBean.ConnInfo();
		org.evd.game.serializeBean.ConnInfoIOSerializer.read(in, connInfo);
		return connInfo;
	}
	public static ISerializable org_evd_game_serializeBean_ConnInfoBaseRead(InputStream in) throws IOException{
		org.evd.game.serializeBean.ConnInfoBase connInfoBase = new org.evd.game.serializeBean.ConnInfoBase();
		org.evd.game.serializeBean.ConnInfoBaseIOSerializer.read(in, connInfoBase);
		return connInfoBase;
	}

}
