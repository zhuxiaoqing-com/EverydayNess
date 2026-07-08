package org.evd.game.runtime.netty;

import io.netty.util.AttributeKey;

public class ServerAttributeKey {

	public static final AttributeKey<Long> channel_Id = AttributeKey.valueOf("channelId");

	public static final AttributeKey<NetChannel> netChannel = AttributeKey.valueOf("netChannel");

	public static final AttributeKey<Integer> brokenType = AttributeKey.valueOf("brokenType");

	public static final AttributeKey<String> remoteNodeId = AttributeKey.valueOf("remoteNodeId");

	public static final AttributeKey<String> serverType = AttributeKey.valueOf("serverType");
	public static final AttributeKey<Integer> serverId = AttributeKey.valueOf("serverId");
	
	
	public static final AttributeKey<String> clientRemoteIp = AttributeKey.valueOf("clientRemoteIp");
	
	//****************** 以下为battle *****************************
	public static final AttributeKey<Integer> battleThreadId = AttributeKey.valueOf("battleThreadId");
	public static final AttributeKey<Long> roleInfo = AttributeKey.valueOf("roleInfo");
	public static final AttributeKey<Long> sceneId = AttributeKey.valueOf("sceneId");

}
