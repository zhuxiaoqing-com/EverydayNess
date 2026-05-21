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
		OutputStream.registerSerializeWriteFunc(2005701922, SerializerRegister::TestSerIOSerializerWrite);
		OutputStream.registerSerializeWriteFunc(1965326063, SerializerRegister::CoInfoIOSerializerWrite);
		OutputStream.registerSerializeWriteFunc(-48846737, SerializerRegister::ConnInfoIOSerializerWrite);
		OutputStream.registerSerializeWriteFunc(-943828000, SerializerRegister::ConnInfoBaseIOSerializerWrite);
	}
	/**
	* 注册反序列化
	*/
	private static void registerRead(){
		InputStream.registerSerializeReadFunc(2005701922, SerializerRegister::TestSerIOSerializerRead);
		InputStream.registerSerializeReadFunc(1965326063, SerializerRegister::CoInfoIOSerializerRead);
		InputStream.registerSerializeReadFunc(-48846737, SerializerRegister::ConnInfoIOSerializerRead);
		InputStream.registerSerializeReadFunc(-943828000, SerializerRegister::ConnInfoBaseIOSerializerRead);
	}
	/**
	* 注册反序列化枚举
	*/
	private static void registerReadEnum(){
	}

	public static void TestSerIOSerializerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.common.TestSerIOSerializer.write(out, (org.evd.game.common.TestSer)ser);
	}
	public static void CoInfoIOSerializerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.serializeBean.CoInfoIOSerializer.write(out, (org.evd.game.serializeBean.CoInfo)ser);
	}
	public static void ConnInfoIOSerializerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.serializeBean.ConnInfoIOSerializer.write(out, (org.evd.game.serializeBean.ConnInfo)ser);
	}
	public static void ConnInfoBaseIOSerializerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.serializeBean.ConnInfoBaseIOSerializer.write(out, (org.evd.game.serializeBean.ConnInfoBase)ser);
	}

	public static ISerializable TestSerIOSerializerRead(InputStream in) throws IOException{
		org.evd.game.common.TestSer testSer = new org.evd.game.common.TestSer();
		org.evd.game.common.TestSerIOSerializer.read(in, testSer);
		return testSer;
	}
	public static ISerializable CoInfoIOSerializerRead(InputStream in) throws IOException{
		org.evd.game.serializeBean.CoInfo coInfo = new org.evd.game.serializeBean.CoInfo();
		org.evd.game.serializeBean.CoInfoIOSerializer.read(in, coInfo);
		return coInfo;
	}
	public static ISerializable ConnInfoIOSerializerRead(InputStream in) throws IOException{
		org.evd.game.serializeBean.ConnInfo connInfo = new org.evd.game.serializeBean.ConnInfo();
		org.evd.game.serializeBean.ConnInfoIOSerializer.read(in, connInfo);
		return connInfo;
	}
	public static ISerializable ConnInfoBaseIOSerializerRead(InputStream in) throws IOException{
		org.evd.game.serializeBean.ConnInfoBase connInfoBase = new org.evd.game.serializeBean.ConnInfoBase();
		org.evd.game.serializeBean.ConnInfoBaseIOSerializer.read(in, connInfoBase);
		return connInfoBase;
	}

}