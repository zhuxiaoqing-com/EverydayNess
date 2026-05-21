package org.evd.game.runtime.netty;

import com.google.protobuf.GeneratedMessage;

/**
 * @author zhuxiaoqing
 * @Description: Message
 * @Date 2026/5/21 14:48
 **/
public class Message {
    /** 消息最大 {@link Integer#MAX_VALUE} 消息头长度 4字节 **/
    public static final int MAX_MESSAGE_SIZE = Integer.MAX_VALUE, HEAD_SIZE = 4;
    private int cmd;
    /** 消息体 pb等 **/
    private Object obj;
    /** 消息体的 byte[]格式 **/
    private byte[] byteObj;

    private NetChannel channel;

    private String userId;

    private long playerId;

    // 哪个服务器发送过来的消息
    private int serverId;

    private long ChannelId;

    private int version;

    public long sceneId;

    public String remoteClientIp;

    /**
     * 在写业务的时候不要使用该方法方便扩展 替代方案{@link Message#newMessage()}
     */
    protected Message() {

    }

    /** 获取一个新message **/
    public static Message newMessage() {
        return new Message();
    }

    @SuppressWarnings("unchecked")
    public <T> T getBody() {
        return (T) obj;
    }

    public void setBody(Object obj) {
        this.obj = obj;
    }

    @SuppressWarnings("rawtypes")
    public void setBody(GeneratedMessage.Builder builder) {
        this.obj = builder.build();
    }


    public int getCmd() {
        return cmd;
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    public Object getObj() {
        return obj;
    }

    public void setObj(Object obj) {
        this.obj = obj;
    }

    public byte[] getByteObj() {
        return byteObj;
    }

    public void setByteObj(byte[] byteObj) {
        this.byteObj = byteObj;
    }

    public NetChannel getChannel() {
        return channel;
    }

    public void setChannel(NetChannel channel) {
        this.channel = channel;
    }

    public long getChannelId() {
        return ChannelId;
    }

    public void setChannelId(long channelId) {
        ChannelId = channelId;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public String getUserId() {
        return userId == null ? "" : userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getRemoteClientIp() {
        return remoteClientIp == null ? "" : remoteClientIp;
    }

    public void setRemoteClientIp(String remoteClientIp) {
        this.remoteClientIp = remoteClientIp;
    }
}
