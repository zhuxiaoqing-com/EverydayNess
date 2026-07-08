package org.evd.game.runtime;

import io.netty.channel.Channel;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallNodeServicesSync;
import org.evd.game.runtime.call.CallPing;
import org.evd.game.runtime.call.CallPong;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.AddressInfo;
import org.evd.game.runtime.netty.BaseChannelInitializer;
import org.evd.game.runtime.netty.ChannelManager;
import org.evd.game.runtime.netty.NetConnector;
import org.evd.game.runtime.netty.RemoteNodeChannelHandler;
import org.evd.game.runtime.netty.ServerAttributeKey;
import org.evd.game.runtime.serialize.OutputStream;
import org.evd.game.runtime.support.LogCore;

import java.net.InetSocketAddress;

/**
 * 远程Node
 */
public class RemoteNode {
    /** 连接检测时间间隔 3秒 */
    public static final long INTERVAL_PING = 3000;
    /** 连接丢失时间间隔 8秒 */
    public static final long INTERVAL_LOST = 8000;
    /** 主动重连的基础退让间隔 2 秒，后续按指数退让递增。 */
    private static final long INTERVAL_RECONNECT_BASE = 2000;
    /** 主动重连的最大退让间隔 60 秒。 */
    private static final long INTERVAL_RECONNECT_MAX = 60_000;

    /** 远程Node名称 */
    private final String remoteId;
    /** 远程Node地址 */
    private final AddressInfo remoteAddr;
    /** 当前节点是否负责主动建连 */
    private final boolean needConnect;
    /** 本地Node名称 */
    private final Node localNode;

    private volatile long lastRecvTime;
    private volatile long lastConnectAttemptTime;
    /** 下一次允许发起主动重连前需要等待的退让时长。 */
    private volatile long reconnectInterval;
    private volatile Channel channel;
    private volatile boolean active;

    private final NetConnector connector;
    private final ChannelManager channelManager = new ChannelManager();

    public RemoteNode(Node localNode, String remoteName, String remoteAddr, boolean needConnect) {
        this.localNode = localNode;
        this.remoteId = remoteName;
        this.remoteAddr = new AddressInfo(remoteAddr);
        this.needConnect = needConnect;
        if (needConnect) {
            int port = this.remoteAddr.getPort();
            this.connector = new NetConnector(getRemoteId(),
                    new BaseChannelInitializer(new RemoteNodeChannelHandler(channelManager, this), true));
            LogCore.core.info("Netty 连接器初始化完成: remoteNode={}, port={}, needConnect={}", getRemoteId(), port, true);
        } else {
            this.connector = null;
        }
    }

    /**
     * 心跳操作
     */
    public void pulse() {
        long timeCurr = now();
        ensureConnected(timeCurr);
        if (needConnect) {
            sendPing();
        }
        checkChannelTimeout(timeCurr);
    }

    public void onOutboundChannelActive(Channel channel) {
        if (channel == null) {
            return;
        }
        bindChannel(channel);
        sendNodeServicesSync(channel, true);
    }

    public void onNodeServicesSync_nt(Channel channel, boolean init) {
        if (channel == null) {
            return;
        }
        bindChannel(channel);
        if (init) {
            sendNodeServicesSync(channel, false);
        }
    }

    public void onPing_nt(Channel channel) {
        if (channel == null) {
            return;
        }
        if (this.channel == channel) {
            lastRecvTime = now();
        }
        sendPong(channel);
    }

    public void onPong_nt(Channel channel) {
        if (channel == null || this.channel != channel) {
            return;
        }
        lastRecvTime = now();
    }

    /**
     * 是否为逻辑活跃状态
     */
    public boolean isActive() {
        Channel activeChannel = channel;
        return active && activeChannel != null && activeChannel.isActive();
    }

    public void close() {
        if (connector != null) {
            connector.shutdown();
        }
        Channel activeChannel = channel;
        if (activeChannel != null) {
            activeChannel.close();
        }
    }

    /**
     * 发送业务或服务同步请求
     */
    public void sendCall(CallBase call) {
        send(encode(call));
    }

    /**
     * 发送业务或服务同步请求
     * 只有逻辑UP时才允许发业务RPC
     */
    public void send(byte[] buf) {
        Channel channel = this.channel;
        if (!isActive() || channel == null || !channel.isActive()) {
            LogCore.remote.warn("远程Node不可用，丢弃消息: localNode={}, remoteNode={}, needConnect={}",
                    localNode.getId(), remoteId, needConnect);
            return;
        }
        channel.writeAndFlush(buf);
    }

    public String getRemoteId() {
        return remoteId;
    }

    public Node getLocalNode() {
        return localNode;
    }

    private void sendPing() {
        Channel channel = this.channel;
        if (channel == null || !channel.isActive()) {
            return;
        }

        CallPing call = new CallPing();
        call.from = new CallPoint(localNode.getId(), null);
        call.to = new CallPoint(remoteId, null);
        call.addr = localNode.getAddr();

        sendOnChannel(channel, call);
    }

    private void sendPong(Channel channel) {
        CallPong call = new CallPong();
        call.from = new CallPoint(localNode.getId(), null);
        call.to = new CallPoint(remoteId, null);
        sendOnChannel(channel, call);
    }

    private void sendNodeServicesSync(Channel channel, boolean init) {
        CallNodeServicesSync call = new CallNodeServicesSync();
        call.from = new CallPoint(localNode.getId(), null);
        call.to = new CallPoint(remoteId, null);
        call.setInit(init);
        call.setAddr(localNode.getAddr());
        call.setServices(localNode.buildLocalServicesSnapshot());
        sendOnChannel(channel, call);
    }

    /** 握手/心跳直接走指定物理连接，不经过逻辑上线判定。 */
    private void sendOnChannel(Channel channel, CallBase call) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        channel.writeAndFlush(encode(call));
    }

    private byte[] encode(CallBase call) {
        try (OutputStream out = new OutputStream()) {
            out.write(call);

            byte[] copy = new byte[out.getLength()];
            System.arraycopy(out.getBuffer(), 0, copy, 0, out.getLength());
            return copy;
        }
    }

    private void ensureConnected(long timeCurr) {
        if (!needConnect) {
            return;
        }
        if (isActive() || connector.isConnecting()) {
            return;
        }
        if (timeCurr - lastConnectAttemptTime < reconnectInterval) {
            return;
        }

        long reconnectDelay = reconnectInterval;
        lastConnectAttemptTime = timeCurr;
        // 首次断线后立即重试；后续失败按 2s、4s、8s... 指数退让，最多 60s。
        reconnectInterval = reconnectInterval <= 0L
                ? INTERVAL_RECONNECT_BASE
                : Math.min(reconnectInterval * 2L, INTERVAL_RECONNECT_MAX);
        try {
            LogCore.remote.info("远程Node发起重连: localNode={}, remoteNode={}, addr={}, backoffMs={}",
                    localNode.getId(), remoteId, remoteAddr.getHost() + ":" + remoteAddr.getPort(), reconnectDelay);
            connector.tryConnect(false, new InetSocketAddress(remoteAddr.getHost(), remoteAddr.getPort()));
        } catch (Exception e) {
            LogCore.remote.error("远程Node发起重连失败: localNode={}, remoteNode={}, addr={}, backoffMs={}",
                    localNode.getId(), remoteId, remoteAddr.getHost() + ":" + remoteAddr.getPort(), reconnectDelay, e);
        }
    }

    private void checkChannelTimeout(long timeCurr) {
        Channel activeChannel = channel;
        if (activeChannel == null || !activeChannel.isActive()) {
            return;
        }
        if (lastRecvTime <= 0L) {
            return;
        }
        if ((timeCurr - lastRecvTime) <= INTERVAL_LOST) {
            return;
        }

        LogCore.remote.warn("远程Node链路超时: localNode={}, remoteNode={}, needConnect={}",
                localNode.getId(), remoteId, needConnect);
        activeChannel.close();
    }

    public synchronized boolean onChannelDown(Channel channel) {
        if (channel == null || this.channel != channel) {
            return false;
        }
        this.channel = null;
        lastRecvTime = 0L;
        if (active) {
            active = false;
            LogCore.remote.warn("远程Node逻辑下线: localNode={}, remoteNode={}", localNode.getId(), remoteId);
            return true;
        }
        return false;
    }

    private long now() {
        long tickTime = localNode.getTimeCurrent();
        return tickTime > 0L ? tickTime : System.currentTimeMillis();
    }

    private synchronized void bindChannel(Channel channel) {
        Channel oldChannel = this.channel;
        if (oldChannel != null && oldChannel != channel) {
            oldChannel.close();
        }
        this.channel = channel;
        // 一旦链路重新握手成功，重连退让立即清零，下一次断线从基础间隔重新开始。
        reconnectInterval = 0L;
        channel.attr(ServerAttributeKey.remoteNodeId).set(remoteId);
        lastRecvTime = now();
        if (!active) {
            active = true;
            LogCore.remote.info("远程Node逻辑上线: localNode={}, remoteNode={}, needConnect={}",
                    localNode.getId(), remoteId, needConnect);
        }
    }
}
