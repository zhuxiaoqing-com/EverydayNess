package org.evd.game.runtime.netty;

import io.netty.channel.Channel;
import io.netty.util.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {
    private final Map<Long, NetChannel> channelMap = new ConcurrentHashMap<>();

    public NetChannel getChannel(long channelId) {
        return channelMap.get(channelId);
    }

    public NetChannel addChannel(NetChannel netChannel) {
        netChannel.setLastPingTime(System.currentTimeMillis());
        channelMap.put(netChannel.getChannelId(), netChannel);
        return netChannel;
    }

    public void removeChannel(long channelId) {
        channelMap.remove(channelId);
    }

    public void removeChannel(Channel channel) {
        Attribute<Long> attribute = channel.attr(ServerAttributeKey.channel_Id);
        if (attribute == null || attribute.get() == null) {
            //log.error("delete a channel without id");
            return;
        }
        this.channelMap.remove(attribute.get());
    }

    public void clear() {
        channelMap.clear();
    }

    public List<NetChannel> snapshotChannels() {
        return new ArrayList<>(channelMap.values());
    }
}
