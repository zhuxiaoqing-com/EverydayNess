package org.evd.game.runtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallNodeServicesSync;
import org.evd.game.runtime.call.CallPing;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallPong;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.call.CallServiceStop;
import org.evd.game.runtime.call.NodeServiceStatus;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.netty.*;
import org.evd.game.runtime.serialize.OutputStream;
import org.evd.game.runtime.serializeBean.NodeFrameChunk;
import org.evd.game.runtime.support.LogCore;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程Node
 */
@Slf4j
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

    private long lastConnectAttemptTime;
    /** 下一次允许发起主动重连前需要等待的退让时长。 */
    private long reconnectInterval;
    /** 当前物理链路对应的 Session；每次新建连接都会创建新的 Session。 */
    private volatile RemoteSession currentSession;
    private volatile Map<String, NodeServiceStatus> remoteServiceStatuses = Map.of();
    private volatile long remoteServiceStatusTime;

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
                    new BaseChannelInitializer(() -> new RemoteNodeChannelHandler(channelManager, this), false));
            LogCore.core.info("Netty 连接器初始化完成: remoteNode={}, port={}, needConnect={}", getRemoteId(), port, true);
        } else {
            this.connector = null;
        }
    }

    private long now() {
        long tickTime = localNode.getTimeCurrent();
        return tickTime > 0L ? tickTime : System.currentTimeMillis();
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
        checkOutboundChannelTimeout(timeCurr);
    }


    /**
     * 是否为逻辑活跃状态
     */
    public boolean isActive() {
        RemoteSession session = currentSession;
        return session != null && session.isValid();
    }

    RemoteSession captureSession(CallBase call) {
        RemoteSession session = currentSession;
        if (session == null || !session.isValid()
                || !canSendBusinessCall_nt(call)) {
            return null;
        }
        return session;
    }

    boolean isCurrentSession(long expectedSessionId) {
        RemoteSession session = currentSession;
        return session != null
                && session.getSessionId() == expectedSessionId
                && session.isValid();
    }

    public void close() {
        if (connector != null) {
            connector.shutdown();
        }
        RemoteSession session = currentSession;
        if (session != null) {
            session.getChannel().close();
        }
    }

    /**
     * 发送业务或服务同步请求
     */
    public ChannelFuture sendCall(CallBase call) {
        RemoteSession session = currentSession;
        NetChannel activeChannel = session == null ? null : session.getChannel();
        DebugPrint.printSendRpc(activeChannel, call);
        if (!isActive() || activeChannel == null || !activeChannel.isValid()) {
            LogCore.remote.warn("远程Node不可用，丢弃消息: localNode={}, remoteNode={}, needConnect={}",
                    localNode.getId(), remoteId, needConnect);
            return null;
        }
        if (!canSendBusinessCall_nt(call)) {
            LogCore.remote.warn(
                    "远程Node Service当前不可接收业务RPC，丢弃消息: localNode={}, remoteNode={}, service={}, callType={}",
                    localNode.getId(), remoteId,
                    call == null || call.getTo() == null ? null : call.getTo().servId,
                    call == null ? null : call.getClass().getSimpleName());
            return null;
        }
        ByteBuf byteBuf = encodeCall_nt(call).getByteBuf();
        DebugPrint.printSendNodeFrame("sendCall", localNode.getId(), remoteId, activeChannel, byteBuf, call);
        ChannelFuture writeFuture = activeChannel.tryWrite(byteBuf);
        if (writeFuture == null) {
            LogCore.remote.error("remote control/rpc frame rejected by Netty backpressure: localNode={}, remoteNode={}, callType={}",
                    localNode.getId(), remoteId, call.getClass().getSimpleName());
            activeChannel.close();
        }
        return writeFuture;
    }

    /** 将 RPC 结果写回它所属的原始 Session，不允许迁移到重连后的新 Session。 */
    boolean sendCallOnSession(CallBase call, long expectedSessionId) {
        RemoteSession session = currentSession;
        if (session == null || session.getSessionId() != expectedSessionId || !session.isValid()) {
            return false;
        }
        NetChannel targetChannel = session.getChannel();
        DebugPrint.printSendRpc(targetChannel, call);
        ByteBuf byteBuf = encodeCall_nt(call).getByteBuf();
        DebugPrint.printSendNodeFrame("sendCallResult", localNode.getId(), remoteId, targetChannel, byteBuf, call);
        if (targetChannel.tryWrite(byteBuf) != null) {
            return true;
        }
        targetChannel.close();
        return false;
    }

    /** 接收远端 Ping/Pong 携带的 Service 状态。 */
    public void updateServiceStatuses_nt(List<NodeServiceStatus> statuses) {
        Map<String, NodeServiceStatus> snapshot = new HashMap<>();
        if (statuses != null) {
            for (NodeServiceStatus status : statuses) {
                if (status != null && status.getServiceId() != null) {
                    snapshot.put(status.getServiceId(), status);
                }
            }
        }
        remoteServiceStatuses = Map.copyOf(snapshot);
        remoteServiceStatusTime = System.currentTimeMillis();
    }

    boolean canSendBusinessCall_nt(CallBase call) {
        if (call instanceof CallPing
                || call instanceof CallPong
                || call instanceof CallNodeServicesSync
                || call instanceof CallServiceStop
                || call instanceof CallResult) {
            return true;
        }
        if (call == null || call.getTo() == null || call.getTo().servId == null) {
            return false;
        }
        long statusAge = System.currentTimeMillis() - remoteServiceStatusTime;
        if (remoteServiceStatusTime <= 0L
                || statusAge > NetConstants.SERVICE_STATUS_TIMEOUT_MILLIS) {
            return false;
        }
        NodeServiceStatus status = remoteServiceStatuses.get(call.getTo().servId);
        if (status == null) {
            return false;
        }
        return status.getReadyContinuations() < NetConstants.SERVICE_STATUS_BUSY_BACKLOG;
    }

    /** 握手/心跳直接走指定物理连接，不经过逻辑上线判定。 */
    private void sendOnChannel(NetChannel channel, CallBase call) {
        if (channel == null || !channel.isValid()) {
            return;
        }

        DebugPrint.printSendRpc(channel, call);
        ByteBuf byteBuf = encodeCall_nt(call).getByteBuf();
        DebugPrint.printSendNodeFrame("sendOnChannel", localNode.getId(), remoteId, channel, byteBuf, call);
        if (!channel.write(byteBuf)) {
            LogCore.remote.error("node handshake frame rejected by Netty backpressure: localNode={}, remoteNode={}, callType={}",
                    localNode.getId(), remoteId, call.getClass().getSimpleName());
            channel.close();
        }
    }


    /**
     * 发送业务或服务同步请求
     * 只有逻辑UP时才允许发业务RPC
     */
    public boolean send(NodeFrameChunk packet) {
        RemoteSession session = currentSession;
        return send(packet, session == null ? -1L : session.getSessionId());
    }

    public boolean send(NodeFrameChunk packet, long expectedSessionId) {
        RemoteSession session = currentSession;
        if (!isActive() || session == null || !session.isValid()
                || session.getSessionId() != expectedSessionId) {
            LogCore.remote.warn("远程Node不可用，丢弃消息: localNode={}, remoteNode={}, needConnect={}",
                    localNode.getId(), remoteId, needConnect);
            return false;
        }
        NetChannel channel = session.getChannel();
        ByteBuf byteBuf = packet.getByteBuf();
        DebugPrint.printSendNodeFrame("sendPacket", localNode.getId(), remoteId, channel, byteBuf, null);
        if (!channel.write(byteBuf)) {
            channel.close();
            return false;
        }
        return true;
    }


    NodeFrameChunk encodeCall_nt(CallBase call) {
        try (OutputStream out = new OutputStream()) {
            out.write(call);
            return NodeFrameChunk.wrap(out.getBuffer(), out.getLength());
        }
    }

    public String getRemoteId() {
        return remoteId;
    }

    public Node getLocalNode() {
        return localNode;
    }

    private void sendPing() {
        RemoteSession session = currentSession;
        NetChannel channel = session == null ? null : session.getChannel();
        if (channel == null || !channel.isValid()) {
            return;
        }

        CallPing call = new CallPing();
        call.from = new CallPoint(localNode.getId(), null);
        call.to = new CallPoint(remoteId, null);
        call.addr = localNode.getAddr();
        call.setServiceStatuses(localNode.buildLocalServiceStatuses_nt());

        sendOnChannel(channel, call);
    }

    private void sendNodeServicesSync(NetChannel channel, boolean init) {
        CallNodeServicesSync call = new CallNodeServicesSync();
        call.from = new CallPoint(localNode.getId(), null);
        call.to = new CallPoint(remoteId, null);
        call.setInit(init);
        call.setAddr(localNode.getAddr());
        call.setServices(localNode.buildLocalServicesSnapshot());
        sendOnChannel(channel, call);
    }


    public void onOutboundChannelActive(NetChannel channel) {
        if (channel == null) {
            return;
        }
        channel.getChannel().attr(ServerAttributeKey.remoteNodeId).set(remoteId);
        if (!bindChannel(channel)) {
            return;
        }
        sendNodeServicesSync(channel, true);
    }

    public boolean onNodeServicesSync_nt(NetChannel channel) {
        if (channel == null) {
            return false;
        }
        if (!bindChannel(channel)) {
            return false;
        }
        sendNodeServicesSync(channel, false);
        return true;
    }

    public boolean onChannelInactive_nt(NetChannel netChannel, RemoteSession endedSession) {
        if (currentSession == null || !currentSession.matches(netChannel) || currentSession != endedSession) {
            long sessionId = currentSession == null ? -1L : currentSession.getSessionId();
            log.info("远程Node旧 Session 已断开: localNode={}, remoteNode={}, currentSessionId={}, endedSessionId={}, channelId={}",
                    localNode.getId(), remoteId, sessionId,
                    endedSession == null ? -1L : endedSession.getSessionId(), netChannel.getChannelId());
            return false;
        }
        this.currentSession = null;
        remoteServiceStatuses = Map.of();
        remoteServiceStatusTime = 0L;
        LogCore.remote.warn("远程Node逻辑下线: localNode={}, remoteNode={}", localNode.getId(), remoteId);
        return true;
    }


    private boolean bindChannel(NetChannel channel) {
        RemoteSession oldSession = currentSession;
        if (oldSession != null && oldSession.matches(channel)) {
            return true;
        }
        if (oldSession != null && channel.getChannelId() <= oldSession.getChannelId()) {
            LogCore.remote.warn(
                    "拒绝迟到的旧远程连接: localNode={}, remoteNode={}, currentChannelId={}, rejectedChannelId={}",
                    localNode.getId(), remoteId, oldSession.getChannelId(), channel.getChannelId());
            channel.close();
            return false;
        }
        RemoteSession session = new RemoteSession(remoteId, channel);
        channel.getChannel().attr(ServerAttributeKey.remoteSession).set(session);
        remoteServiceStatuses = Map.of();
        remoteServiceStatusTime = 0L;
        currentSession = session;
        if (oldSession != null) {
            LogCore.remote.info("旧的远程Node关闭: localNode={}, remoteNode={}, needConnect={} oldChannelId {} ",
                    localNode.getId(), remoteId, needConnect, oldSession.getChannelId());
            oldSession.getChannel().close();
        }
        // 一旦链路重新握手成功，重连退让立即清零，下一次断线从基础间隔重新开始。
        reconnectInterval = 0L;
        channel.getChannel().attr(ServerAttributeKey.remoteNodeId).set(remoteId);
        LogCore.remote.info("远程Node逻辑上线: localNode={}, remoteNode={}, needConnect={} sessionId={} channelId={}",
                localNode.getId(), remoteId, needConnect, session.getSessionId(), channel.getChannelId());
        return true;
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
            LogCore.remote.warn("远程Node发起重连: localNode={}, remoteNode={}, addr={}, backoffMs={}",
                    localNode.getId(), remoteId, remoteAddr.getHost() + ":" + remoteAddr.getPort(), reconnectDelay);
            connector.tryConnect(false, new InetSocketAddress(remoteAddr.getHost(), remoteAddr.getPort()));
        } catch (Exception e) {
            LogCore.remote.error("远程Node发起重连失败: localNode={}, remoteNode={}, addr={}, backoffMs={}",
                    localNode.getId(), remoteId, remoteAddr.getHost() + ":" + remoteAddr.getPort(), reconnectDelay);
        }
    }


    private void checkOutboundChannelTimeout(long timeCurr) {
        for (NetChannel netChannel : channelManager.snapshotChannels()) {
            long lastActivityTime = netChannel.getLastPingTime();
            if (lastActivityTime <= 0L || (timeCurr - lastActivityTime) <= INTERVAL_LOST) {
                continue;
            }
            LogCore.remote.warn("remoteNode checkTimeout : localNode={}, remoteNode={}, sessionId={}, needConnect={} timeCurr {}  lastActivityTime {}",
                    localNode.getId(), remoteId, netChannel.getChannelId(), needConnect, timeCurr, lastActivityTime);
            netChannel.close();
        }
    }


}
