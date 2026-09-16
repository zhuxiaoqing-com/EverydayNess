package org.evd.game.ConnService.client;

import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.ChannelManager;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;

final class ConnServiceHeartbeatScanner {
    private final ConnClientConnection connection;
    private final ChannelManager channelManager;

    ConnServiceHeartbeatScanner(ConnClientConnection connection, ChannelManager channelManager) {
        this.connection = connection;
        this.channelManager = channelManager;
    }

    void scanTimeoutSessions(long timeoutMillis) {
        long nowMillis = System.currentTimeMillis();
        List<NetChannel> timeoutChannels = new ArrayList<>();
        for (NetChannel channel : channelManager.getChannelMap().values()) {
            long idleMillis = nowMillis - channel.getLastPingTime();
            if (idleMillis < timeoutMillis) {
                continue;
            }
            LogCore.core.warn("ConnService 心跳超时: sessionId={}, remote={}, idleMillis={}, timeoutMillis={}",
                    channel.getChannelId(), channel.getRemoteAddress(), idleMillis, timeoutMillis);
            timeoutChannels.add(channel);
        }
        for (NetChannel channel : timeoutChannels) {
            connection.owner().closeSession(channel.getChannelId(), BrokenType.HEARTBEAT_TIMEOUT.getCode(),
                    "heartbeat timeout after " + timeoutMillis + "ms");
        }
    }
}
