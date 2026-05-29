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
		OutputStream.registerSerializeWriteFunc(-1635775304, SerializerRegister::org_evd_game_DbEntity_serialize_DbFieldWrite);
		OutputStream.registerSerializeWriteFunc(16179613, SerializerRegister::org_evd_game_DbEntity_serialize_DbKeyWrite);
		OutputStream.registerSerializeWriteFunc(15233020, SerializerRegister::org_evd_game_DbEntity_serialize_DBReqWrite);
		OutputStream.registerSerializeWriteFunc(15233453, SerializerRegister::org_evd_game_DbEntity_serialize_DBRspWrite);
		OutputStream.registerSerializeWriteFunc(-1381965618, SerializerRegister::org_evd_game_DbEntity_serialize_DbTableFieldWrite);
		OutputStream.registerSerializeWriteFunc(-1621230289, SerializerRegister::org_evd_game_DbEntity_serialize_DbValueWrite);
		OutputStream.registerSerializeWriteFunc(-430371200, SerializerRegister::org_evd_game_DbEntity_serialize_MysqlReqWrite);
		OutputStream.registerSerializeWriteFunc(-430370767, SerializerRegister::org_evd_game_DbEntity_serialize_MysqlRspWrite);
		OutputStream.registerSerializeWriteFunc(1424771342, SerializerRegister::org_evd_game_DbEntity_serialize_ObjectValueWrite);
		OutputStream.registerSerializeWriteFunc(1965326063, SerializerRegister::org_evd_game_serializeBean_CoInfoWrite);
		OutputStream.registerSerializeWriteFunc(-48846737, SerializerRegister::org_evd_game_serializeBean_ConnInfoWrite);
		OutputStream.registerSerializeWriteFunc(-943828000, SerializerRegister::org_evd_game_serializeBean_ConnInfoBaseWrite);
	}
	/**
	* 注册反序列化
	*/
	private static void registerRead(){
		InputStream.registerSerializeReadFunc(2005701922, SerializerRegister::org_evd_game_common_TestSerRead);
		InputStream.registerSerializeReadFunc(-1635775304, SerializerRegister::org_evd_game_DbEntity_serialize_DbFieldRead);
		InputStream.registerSerializeReadFunc(16179613, SerializerRegister::org_evd_game_DbEntity_serialize_DbKeyRead);
		InputStream.registerSerializeReadFunc(15233020, SerializerRegister::org_evd_game_DbEntity_serialize_DBReqRead);
		InputStream.registerSerializeReadFunc(15233453, SerializerRegister::org_evd_game_DbEntity_serialize_DBRspRead);
		InputStream.registerSerializeReadFunc(-1381965618, SerializerRegister::org_evd_game_DbEntity_serialize_DbTableFieldRead);
		InputStream.registerSerializeReadFunc(-1621230289, SerializerRegister::org_evd_game_DbEntity_serialize_DbValueRead);
		InputStream.registerSerializeReadFunc(-430371200, SerializerRegister::org_evd_game_DbEntity_serialize_MysqlReqRead);
		InputStream.registerSerializeReadFunc(-430370767, SerializerRegister::org_evd_game_DbEntity_serialize_MysqlRspRead);
		InputStream.registerSerializeReadFunc(1424771342, SerializerRegister::org_evd_game_DbEntity_serialize_ObjectValueRead);
		InputStream.registerSerializeReadFunc(1965326063, SerializerRegister::org_evd_game_serializeBean_CoInfoRead);
		InputStream.registerSerializeReadFunc(-48846737, SerializerRegister::org_evd_game_serializeBean_ConnInfoRead);
		InputStream.registerSerializeReadFunc(-943828000, SerializerRegister::org_evd_game_serializeBean_ConnInfoBaseRead);
	}
	/**
	* 注册反序列化枚举
	*/
	private static void registerReadEnum(){
		InputStream.registerSerializeReadEnumFunc(2035032454, SerializerRegister::org_evd_game_DbEntity_serialize_DbDataTypeReadEnum);
		InputStream.registerSerializeReadEnumFunc(23776887, SerializerRegister::org_evd_game_DbEntity_serialize_DbKeyTypeReadEnum);
		InputStream.registerSerializeReadEnumFunc(1094206653, SerializerRegister::org_evd_game_DbEntity_serialize_DbOpTypeReadEnum);
		InputStream.registerSerializeReadEnumFunc(-1730817783, SerializerRegister::org_evd_game_DbEntity_serialize_DbValueTypeReadEnum);
	}

	public static void org_evd_game_common_TestSerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.common.TestSerIOSerializer.write(out, (org.evd.game.common.TestSer)ser);
	}
	public static void org_evd_game_DbEntity_serialize_DbFieldWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.DbFieldIOSerializer.write(out, (org.evd.game.DbEntity.serialize.DbField)ser);
	}
	public static void org_evd_game_DbEntity_serialize_DbKeyWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.DbKeyIOSerializer.write(out, (org.evd.game.DbEntity.serialize.DbKey)ser);
	}
	public static void org_evd_game_DbEntity_serialize_DBReqWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.DBReqIOSerializer.write(out, (org.evd.game.DbEntity.serialize.DBReq)ser);
	}
	public static void org_evd_game_DbEntity_serialize_DBRspWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.DBRspIOSerializer.write(out, (org.evd.game.DbEntity.serialize.DBRsp)ser);
	}
	public static void org_evd_game_DbEntity_serialize_DbTableFieldWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.DbTableFieldIOSerializer.write(out, (org.evd.game.DbEntity.serialize.DbTableField)ser);
	}
	public static void org_evd_game_DbEntity_serialize_DbValueWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.DbValueIOSerializer.write(out, (org.evd.game.DbEntity.serialize.DbValue)ser);
	}
	public static void org_evd_game_DbEntity_serialize_MysqlReqWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.MysqlReqIOSerializer.write(out, (org.evd.game.DbEntity.serialize.MysqlReq)ser);
	}
	public static void org_evd_game_DbEntity_serialize_MysqlRspWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.MysqlRspIOSerializer.write(out, (org.evd.game.DbEntity.serialize.MysqlRsp)ser);
	}
	public static void org_evd_game_DbEntity_serialize_ObjectValueWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.DbEntity.serialize.ObjectValueIOSerializer.write(out, (org.evd.game.DbEntity.serialize.ObjectValue)ser);
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
	public static ISerializable org_evd_game_DbEntity_serialize_DbFieldRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.DbField dbField = new org.evd.game.DbEntity.serialize.DbField();
		org.evd.game.DbEntity.serialize.DbFieldIOSerializer.read(in, dbField);
		return dbField;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_DbKeyRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.DbKey dbKey = new org.evd.game.DbEntity.serialize.DbKey();
		org.evd.game.DbEntity.serialize.DbKeyIOSerializer.read(in, dbKey);
		return dbKey;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_DBReqRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.DBReq dBReq = new org.evd.game.DbEntity.serialize.DBReq();
		org.evd.game.DbEntity.serialize.DBReqIOSerializer.read(in, dBReq);
		return dBReq;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_DBRspRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.DBRsp dBRsp = new org.evd.game.DbEntity.serialize.DBRsp();
		org.evd.game.DbEntity.serialize.DBRspIOSerializer.read(in, dBRsp);
		return dBRsp;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_DbTableFieldRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.DbTableField dbTableField = new org.evd.game.DbEntity.serialize.DbTableField();
		org.evd.game.DbEntity.serialize.DbTableFieldIOSerializer.read(in, dbTableField);
		return dbTableField;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_DbValueRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.DbValue dbValue = new org.evd.game.DbEntity.serialize.DbValue();
		org.evd.game.DbEntity.serialize.DbValueIOSerializer.read(in, dbValue);
		return dbValue;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_MysqlReqRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.MysqlReq mysqlReq = new org.evd.game.DbEntity.serialize.MysqlReq();
		org.evd.game.DbEntity.serialize.MysqlReqIOSerializer.read(in, mysqlReq);
		return mysqlReq;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_MysqlRspRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.MysqlRsp mysqlRsp = new org.evd.game.DbEntity.serialize.MysqlRsp();
		org.evd.game.DbEntity.serialize.MysqlRspIOSerializer.read(in, mysqlRsp);
		return mysqlRsp;
	}
	public static ISerializable org_evd_game_DbEntity_serialize_ObjectValueRead(InputStream in) throws IOException{
		org.evd.game.DbEntity.serialize.ObjectValue objectValue = new org.evd.game.DbEntity.serialize.ObjectValue();
		org.evd.game.DbEntity.serialize.ObjectValueIOSerializer.read(in, objectValue);
		return objectValue;
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

	public static Enum<?> org_evd_game_DbEntity_serialize_DbDataTypeReadEnum(InputStream in, int ordinal) throws IOException{
		return org.evd.game.DbEntity.serialize.DbDataType.values()[ordinal];
	}
	public static Enum<?> org_evd_game_DbEntity_serialize_DbKeyTypeReadEnum(InputStream in, int ordinal) throws IOException{
		return org.evd.game.DbEntity.serialize.DbKeyType.values()[ordinal];
	}
	public static Enum<?> org_evd_game_DbEntity_serialize_DbOpTypeReadEnum(InputStream in, int ordinal) throws IOException{
		return org.evd.game.DbEntity.serialize.DbOpType.values()[ordinal];
	}
	public static Enum<?> org_evd_game_DbEntity_serialize_DbValueTypeReadEnum(InputStream in, int ordinal) throws IOException{
		return org.evd.game.DbEntity.serialize.DbValueType.values()[ordinal];
	}
}
