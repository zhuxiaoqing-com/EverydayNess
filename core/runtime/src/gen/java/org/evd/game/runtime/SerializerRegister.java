package org.evd.game.runtime;

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
		OutputStream.registerSerializeWriteFunc(1876623077, SerializerRegister::org_evd_game_runtime_actor_ActorAddressWrite);
		OutputStream.registerSerializeWriteFunc(-124463222, SerializerRegister::org_evd_game_runtime_actor_ActorIdWrite);
		OutputStream.registerSerializeWriteFunc(-367084235, SerializerRegister::org_evd_game_runtime_call_ActorMessageWrite);
		OutputStream.registerSerializeWriteFunc(-1065350591, SerializerRegister::org_evd_game_runtime_call_CallWrite);
		OutputStream.registerSerializeWriteFunc(287635283, SerializerRegister::org_evd_game_runtime_call_CallPingWrite);
		OutputStream.registerSerializeWriteFunc(326933455, SerializerRegister::org_evd_game_runtime_call_CallPointWrite);
		OutputStream.registerSerializeWriteFunc(1593330110, SerializerRegister::org_evd_game_runtime_call_CallResultWrite);
		OutputStream.registerSerializeWriteFunc(322098688, SerializerRegister::org_evd_game_runtime_ChunkWrite);
		OutputStream.registerSerializeWriteFunc(-1328685355, SerializerRegister::org_evd_game_runtime_ClientSessionRefWrite);
		OutputStream.registerSerializeWriteFunc(1944252859, SerializerRegister::org_evd_game_runtime_TickTimerWrite);
	}
	/**
	* 注册反序列化
	*/
	private static void registerRead(){
		InputStream.registerSerializeReadFunc(1876623077, SerializerRegister::org_evd_game_runtime_actor_ActorAddressRead);
		InputStream.registerSerializeReadFunc(-124463222, SerializerRegister::org_evd_game_runtime_actor_ActorIdRead);
		InputStream.registerSerializeReadFunc(-367084235, SerializerRegister::org_evd_game_runtime_call_ActorMessageRead);
		InputStream.registerSerializeReadFunc(-1065350591, SerializerRegister::org_evd_game_runtime_call_CallRead);
		InputStream.registerSerializeReadFunc(287635283, SerializerRegister::org_evd_game_runtime_call_CallPingRead);
		InputStream.registerSerializeReadFunc(326933455, SerializerRegister::org_evd_game_runtime_call_CallPointRead);
		InputStream.registerSerializeReadFunc(1593330110, SerializerRegister::org_evd_game_runtime_call_CallResultRead);
		InputStream.registerSerializeReadFunc(322098688, SerializerRegister::org_evd_game_runtime_ChunkRead);
		InputStream.registerSerializeReadFunc(-1328685355, SerializerRegister::org_evd_game_runtime_ClientSessionRefRead);
		InputStream.registerSerializeReadFunc(1944252859, SerializerRegister::org_evd_game_runtime_TickTimerRead);
	}
	/**
	* 注册反序列化枚举
	*/
	private static void registerReadEnum(){
	}

	public static void org_evd_game_runtime_actor_ActorAddressWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.actor.ActorAddressIOSerializer.write(out, (org.evd.game.runtime.actor.ActorAddress)ser);
	}
	public static void org_evd_game_runtime_actor_ActorIdWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.actor.ActorIdIOSerializer.write(out, (org.evd.game.runtime.actor.ActorId)ser);
	}
	public static void org_evd_game_runtime_call_ActorMessageWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.call.ActorMessageIOSerializer.write(out, (org.evd.game.runtime.call.ActorMessage)ser);
	}
	public static void org_evd_game_runtime_call_CallWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.call.CallIOSerializer.write(out, (org.evd.game.runtime.call.Call)ser);
	}
	public static void org_evd_game_runtime_call_CallPingWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.call.CallPingIOSerializer.write(out, (org.evd.game.runtime.call.CallPing)ser);
	}
	public static void org_evd_game_runtime_call_CallPointWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.call.CallPointIOSerializer.write(out, (org.evd.game.runtime.call.CallPoint)ser);
	}
	public static void org_evd_game_runtime_call_CallResultWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.call.CallResultIOSerializer.write(out, (org.evd.game.runtime.call.CallResult)ser);
	}
	public static void org_evd_game_runtime_ChunkWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.ChunkIOSerializer.write(out, (org.evd.game.runtime.Chunk)ser);
	}
	public static void org_evd_game_runtime_ClientSessionRefWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.ClientSessionRefIOSerializer.write(out, (org.evd.game.runtime.ClientSessionRef)ser);
	}
	public static void org_evd_game_runtime_TickTimerWrite(OutputStream out, ISerializable ser) throws IOException{
		org.evd.game.runtime.TickTimerIOSerializer.write(out, (org.evd.game.runtime.TickTimer)ser);
	}

	public static ISerializable org_evd_game_runtime_actor_ActorAddressRead(InputStream in) throws IOException{
		org.evd.game.runtime.actor.ActorAddress actorAddress = new org.evd.game.runtime.actor.ActorAddress();
		org.evd.game.runtime.actor.ActorAddressIOSerializer.read(in, actorAddress);
		return actorAddress;
	}
	public static ISerializable org_evd_game_runtime_actor_ActorIdRead(InputStream in) throws IOException{
		org.evd.game.runtime.actor.ActorId actorId = new org.evd.game.runtime.actor.ActorId();
		org.evd.game.runtime.actor.ActorIdIOSerializer.read(in, actorId);
		return actorId;
	}
	public static ISerializable org_evd_game_runtime_call_ActorMessageRead(InputStream in) throws IOException{
		org.evd.game.runtime.call.ActorMessage actorMessage = new org.evd.game.runtime.call.ActorMessage();
		org.evd.game.runtime.call.ActorMessageIOSerializer.read(in, actorMessage);
		return actorMessage;
	}
	public static ISerializable org_evd_game_runtime_call_CallRead(InputStream in) throws IOException{
		org.evd.game.runtime.call.Call call = new org.evd.game.runtime.call.Call();
		org.evd.game.runtime.call.CallIOSerializer.read(in, call);
		return call;
	}
	public static ISerializable org_evd_game_runtime_call_CallPingRead(InputStream in) throws IOException{
		org.evd.game.runtime.call.CallPing callPing = new org.evd.game.runtime.call.CallPing();
		org.evd.game.runtime.call.CallPingIOSerializer.read(in, callPing);
		return callPing;
	}
	public static ISerializable org_evd_game_runtime_call_CallPointRead(InputStream in) throws IOException{
		org.evd.game.runtime.call.CallPoint callPoint = new org.evd.game.runtime.call.CallPoint();
		org.evd.game.runtime.call.CallPointIOSerializer.read(in, callPoint);
		return callPoint;
	}
	public static ISerializable org_evd_game_runtime_call_CallResultRead(InputStream in) throws IOException{
		org.evd.game.runtime.call.CallResult callResult = new org.evd.game.runtime.call.CallResult();
		org.evd.game.runtime.call.CallResultIOSerializer.read(in, callResult);
		return callResult;
	}
	public static ISerializable org_evd_game_runtime_ChunkRead(InputStream in) throws IOException{
		org.evd.game.runtime.Chunk chunk = new org.evd.game.runtime.Chunk();
		org.evd.game.runtime.ChunkIOSerializer.read(in, chunk);
		return chunk;
	}
	public static ISerializable org_evd_game_runtime_ClientSessionRefRead(InputStream in) throws IOException{
		org.evd.game.runtime.ClientSessionRef clientSessionRef = new org.evd.game.runtime.ClientSessionRef();
		org.evd.game.runtime.ClientSessionRefIOSerializer.read(in, clientSessionRef);
		return clientSessionRef;
	}
	public static ISerializable org_evd_game_runtime_TickTimerRead(InputStream in) throws IOException{
		org.evd.game.runtime.TickTimer tickTimer = new org.evd.game.runtime.TickTimer();
		org.evd.game.runtime.TickTimerIOSerializer.read(in, tickTimer);
		return tickTimer;
	}

}
