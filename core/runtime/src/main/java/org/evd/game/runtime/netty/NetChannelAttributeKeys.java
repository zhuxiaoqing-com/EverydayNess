package org.evd.game.runtime.netty;

import io.netty.util.AttributeKey;

public final class NetChannelAttributeKeys {
    public static final AttributeKey<Long> CHANNEL_ID = AttributeKey.valueOf("runtime.netty.channel_id");
    public static final AttributeKey<NetChannel> NET_CHANNEL = AttributeKey.valueOf("runtime.netty.net_channel");

    private NetChannelAttributeKeys() {
    }
}
