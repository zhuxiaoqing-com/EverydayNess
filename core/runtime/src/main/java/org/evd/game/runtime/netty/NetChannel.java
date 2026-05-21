package org.evd.game.runtime.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Attribute;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class NetChannel {
    public static final int MESSAGE_GW_TIME = 30;
    public static final int MESSAGE_GW_COUNT = 150;

    public static final int MESSAGE_BT_TIME = 30;
    public static final int MESSAGE_BT_COUNT = 200;

    /** 自增的id **/
    private static final AtomicLong autoChannelId = new AtomicLong(0);

    private Channel channel;

    /** 分配的ID */
    private long channelId;

    private final String remoteAddress;

    // 客户端和服务器之间心跳维护
    private long lastPingTime;
    // 开加速器之后心跳间隔变短, 计数, 超过20次就主动提出
    private int invalidPingCount;
    // 正常的计数, 用于清除异常次数
    private int normalPingCount;

    private long playerId;

    private String userId;


    private InetSocketAddress playerEnterAddress;

    private long lastMessageTime;
    private int frequentlyMessageCount;

    // 历史消息
    public List<HisMessage> frequentlyMessageList = new ArrayList<>();

    public String remoteClientIp;

    private final Set<Integer> mergedIds = new HashSet<>();


    public NetChannel(Channel channel) {
        Attribute<NetChannel> netChannel = channel.attr(ServerAttributeKey.netChannel);
        netChannel.set(this);
        Attribute<Long> attribute = channel.attr(ServerAttributeKey.channel_Id);
        this.channelId = autoChannelId.addAndGet(1);
        attribute.set(this.channelId);
        this.channel = channel;
        this.remoteAddress = remoteAddress(channel.remoteAddress());
    }


    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public void write(Object obj) {
        channel.writeAndFlush(obj);
    }

    public void writeAndClose(Object obj) {
        channel.writeAndFlush(obj).addListener(new ChannelFutureListener() {

            public void operationComplete(ChannelFuture future) throws Exception {
                future.channel().close();
            }
        });
    }

    public void close() {
        this.channel.close();
    }

    public <T> T call(Object obj, Class<T> cls) {
        // Response response = new Response();
        //

        // response.waitFor();
        // return (T)response.getBody();
        return null;
    }



    public long getChannelId() {
        return channelId;
    }



    public void setChannelId(long channelId) {
        this.channelId = channelId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public InetSocketAddress getPlayerEnterAddress() {
        return playerEnterAddress;
    }

    public void setPlayerEnterAddress(InetSocketAddress playerEnterAddress) {
        this.playerEnterAddress = playerEnterAddress;
    }

    public static AtomicLong getAutochannelid() {
        return autoChannelId;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public void increasedInvalidPingCount() {
        this.invalidPingCount++;
        this.normalPingCount = 0;
    }

    public void increasedNormalPingCount() {
        this.normalPingCount++;
        if (this.normalPingCount > 5) {
            this.invalidPingCount = 0;
            this.normalPingCount = 0;
        }
    }

    public int getInvalidPingCount() {
        return invalidPingCount;
    }

    public int getFrequentlyMessageCount() {
        return frequentlyMessageCount;
    }

    public void setFrequentlyMessageCount(int frequentlyMessageCount) {
        this.frequentlyMessageCount = frequentlyMessageCount;
    }

    public boolean isValid() {
        return channel != null && channel.isActive();
    }

    public long getLastPingTime() {
        return lastPingTime;
    }

    public void setLastPingTime(long lastPingTime) {
        this.lastPingTime = lastPingTime;
    }

    public List<HisMessage> getFrequentlyMessageList() {
        return frequentlyMessageList;
    }

    public String getRemoteClientIp() {
        return remoteClientIp;
    }

    public void setRemoteClientIp(String remoteClientIp) {
        this.remoteClientIp = remoteClientIp;
    }

    public Set<Integer> getMergedIds() {
        return mergedIds;
    }

    private static String remoteAddress(SocketAddress remoteAddress) {
        return remoteAddress == null ? "unknown" : remoteAddress.toString();
    }
}
