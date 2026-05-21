package org.evd.game.runtime.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

public class NetChannel {
    private final long channelId;
    private final Channel channel;
    private final String remoteAddress;

    public NetChannel(long channelId, Channel channel) {
        this.channelId = channelId;
        this.channel = channel;
        this.remoteAddress = String.valueOf(channel.remoteAddress());
        channel.attr(NetChannelAttributeKeys.CHANNEL_ID).set(channelId);
        channel.attr(NetChannelAttributeKeys.NET_CHANNEL).set(this);
    }

    public long getChannelId() {
        return channelId;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isActive() {
        return channel.isActive();
    }

    public void write(byte[] payload) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(payload));
    }

    public void close() {
        channel.close();
    }

    public Channel unwrap() {
        return channel;
    }
}
