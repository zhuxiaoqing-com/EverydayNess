package org.evd.game.runtime.netty;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {
    private final Map<Long, NetChannel> channelMap = new ConcurrentHashMap<>();

    public NetChannel getChannel(long channelId) {
        return channelMap.get(channelId);
    }

    public NetChannel addChannel(long channelId, Channel channel) {
        NetChannel netChannel = new NetChannel(channelId, channel);
        channelMap.put(channelId, netChannel);
        return netChannel;
    }

    public void removeChannel(long channelId) {
        channelMap.remove(channelId);
    }

    public void removeChannel(Channel channel) {
        Long channelId = channel.attr(NetChannelAttributeKeys.CHANNEL_ID).get();
        if (channelId != null) {
            channelMap.remove(channelId);
        }
    }

    public void clear() {
        channelMap.clear();
    }
}
