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
    /** 当前节点是否负责主动建连。双链 RPC 模式下，每个已配置远端都应为 true。 */
    private final boolean needConnect;
    /** 本地Node名称 */
    private final Node localNode;

    private volatile long lastConnectAttemptTime;
    /** 下一次允许发起主动重连前需要等待的退让时长。 */
    private volatile long reconnectInterval;
    /** 本端主动建立的出站 RPC 链路，仅用于发起业务调用。 */
    private volatile RemoteSession outboundSession;
    /** 对端主动建立的入站 RPC 链路，仅用于接收调用并回写响应。 */
    private volatile RemoteSession inboundSession;
    private volatile boolean active;
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
        RemoteSession session = outboundSession;
        return active && session != null && session.isValid();
    }

    synchronized RemoteSession captureSession(CallBase call) {
        RemoteSession session = outboundSession;
        if (!active || session == null || !session.isValid()
                || !canSendBusinessCall_nt(call)) {
            return null;
        }
        return session;
    }

    boolean isCurrentSession(long expectedSessionId) {
        return isMatchingSession(outboundSession, expectedSessionId)
                || isMatchingSession(inboundSession, expectedSessionId);
    }

    /** 仅用于本端出站缓冲发送前的链路一致性校验。 */
    boolean isCurrentOutboundSession(long expectedSessionId) {
        return isMatchingSession(outboundSession, expectedSessionId);
    }

    /** 仅允许远端主动 RPC 请求从入站 Session 进入。 */
    boolean isCurrentInboundSession(long expectedSessionId) {
        return isMatchingSession(inboundSession, expectedSessionId);
    }

    /** 断链清理前识别等待所绑定的出站 Session；此时 Channel 可能已不可写。 */
    boolean isOutboundSession(RemoteSession session) {
        return session != null && session.isOutbound();
    }

    public void close() {
        if (connector != null) {
            connector.shutdown();
        }
        closeSession(outboundSession);
        closeSession(inboundSession);
    }

    /**
     * 发送业务或服务同步请求
     */
    public ChannelFuture sendCall(CallBase call) {
        RemoteSession session = outboundSession;
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

    /** 将入站 RPC 的响应或链路应答写回其入站 Session，不允许走出站链路。 */
    boolean sendCallOnInboundSession(CallBase call, long expectedSessionId) {
        RemoteSession session = inboundSession;
        if (!isMatchingSession(session, expectedSessionId)) {
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
        RemoteSession session = outboundSession;
        return send(packet, session == null ? -1L : session.getSessionId());
    }

    public boolean send(NodeFrameChunk packet, long expectedSessionId) {
        RemoteSession session = outboundSession;
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
        RemoteSession session = outboundSession;
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
        bindOutboundChannel(channel);
        sendNodeServicesSync(channel, true);
    }

    public void onNodeServicesSync_nt(NetChannel channel) {
        if (channel == null) {
            return;
        }
        bindInboundChannel(channel);
        sendNodeServicesSync(channel, false);
    }

    public synchronized boolean onChannelInactive_nt(NetChannel netChannel, RemoteSession endedSession) {
        if (outboundSession != null && outboundSession.matches(netChannel) && outboundSession == endedSession) {
            outboundSession = null;
            remoteServiceStatuses = Map.of();
            remoteServiceStatusTime = 0L;
            if (active) {
                active = false;
                LogCore.remote.warn("远程Node出站 RPC 链路下线: localNode={}, remoteNode={}", localNode.getId(), remoteId);
            }
            return !hasAnyActiveSession();
        }
        if (inboundSession != null && inboundSession.matches(netChannel) && inboundSession == endedSession) {
            inboundSession = null;
            LogCore.remote.warn("远程Node入站 RPC 链路下线: localNode={}, remoteNode={}", localNode.getId(), remoteId);
            return !hasAnyActiveSession();
        }
        log.info("远程Node旧 Session 已断开: localNode={}, remoteNode={}, outboundSessionId={}, inboundSessionId={}, endedSessionId={}, channelId={}",
                localNode.getId(), remoteId, sessionId(outboundSession), sessionId(inboundSession),
                endedSession == null ? -1L : endedSession.getSessionId(), netChannel.getChannelId());
        return false;
    }


    private synchronized void bindOutboundChannel(NetChannel channel) {
        RemoteSession oldSession = outboundSession;
        RemoteSession session = bindSession(channel, oldSession, RemoteSession.Direction.OUTBOUND);
        outboundSession = session;
        // 一旦链路重新握手成功，重连退让立即清零，下一次断线从基础间隔重新开始。
        reconnectInterval = 0L;
        if (!active) {
            active = true;
            LogCore.remote.info("远程Node出站 RPC 链路上线: localNode={}, remoteNode={}, needConnect={} sessionId={} channelId={}",
                    localNode.getId(), remoteId, needConnect, session.getSessionId(), channel.getChannelId());
        }
        if (oldSession != null && oldSession != session) {
            closeSession(oldSession);
        }
    }

    private synchronized void bindInboundChannel(NetChannel channel) {
        RemoteSession oldSession = inboundSession;
        RemoteSession session = bindSession(channel, oldSession, RemoteSession.Direction.INBOUND);
        inboundSession = session;
        LogCore.remote.info("远程Node入站 RPC 链路上线: localNode={}, remoteNode={}, sessionId={}, channelId={}",
                localNode.getId(), remoteId, session.getSessionId(), channel.getChannelId());
        if (oldSession != null && oldSession != session) {
            closeSession(oldSession);
        }
    }

    private RemoteSession bindSession(NetChannel channel, RemoteSession current, RemoteSession.Direction direction) {
        RemoteSession session = current != null && current.matches(channel)
                ? current
                : new RemoteSession(remoteId, channel, direction);
        channel.getChannel().attr(ServerAttributeKey.remoteSession).set(session);
        channel.getChannel().attr(ServerAttributeKey.remoteNodeId).set(remoteId);
        return session;
    }

    private boolean isMatchingSession(RemoteSession session, long expectedSessionId) {
        return session != null && session.getSessionId() == expectedSessionId && session.isValid();
    }

    private boolean hasAnyActiveSession() {
        return (outboundSession != null && outboundSession.isValid())
                || (inboundSession != null && inboundSession.isValid());
    }

    private long sessionId(RemoteSession session) {
        return session == null ? -1L : session.getSessionId();
    }

    private void closeSession(RemoteSession session) {
        if (session != null) {
            session.getChannel().close();
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
