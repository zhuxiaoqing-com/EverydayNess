package org.evd.game.ConnService.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.evd.game.ConnService.ConnService;
import org.evd.game.ConnService.session.ConnSessionRegistry;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.netty.BaseChannelInitializer;
import org.evd.game.runtime.netty.ChannelManager;
import org.evd.game.runtime.netty.NetAcceptor;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.support.LogCore;

/** ConnService 的客户端连接、协议分发和消息写回。 */
public final class ConnClientConnection {
    public static final long HEARTBEAT_TIMEOUT_MILLIS = 60_000L;

    private final ConnService owner;
    private final ConnSessionRegistry sessionRegistry;
    private final ChannelManager channelManager = new ChannelManager();
    private final ConnServiceClientCmdRouter clientCmdRouter;
    private final ConnServiceHeartbeatScanner heartbeatScanner;

    private volatile NetAcceptor clientAcceptor;

    public ConnClientConnection(ConnService owner, ConnSessionRegistry sessionRegistry) {
        this.owner = owner;
        this.sessionRegistry = sessionRegistry;
        this.clientCmdRouter = new ConnServiceClientCmdRouter(this);
        this.heartbeatScanner = new ConnServiceHeartbeatScanner(this, channelManager);
    }

    public void init() {
        int port = owner.getServiceInfo().getAddressInfo().getPort();
        clientAcceptor = new NetAcceptor(port,
                new BaseChannelInitializer(() -> new ConnServiceClientChannelHandler(this), true));
        LogCore.core.info("ConnService Netty 启动完成: service={}, port={}", owner.getId(), port);
    }

    public void onClose() {
        NetAcceptor acceptor = clientAcceptor;
        clientAcceptor = null;
        if (acceptor != null) {
            acceptor.shutdown();
        }
        channelManager.clear();
    }

    void dispatchClientCmd(NetChannel session, int cmd, Chunk body) {
        if (!session.canProcessClientCmd(cmd)) {
            LogCore.core.warn("ConnService 拒绝非法阶段协议: service={}, sessionId={}, state={}, cmdId={}, userId={}, playerId={}",
                    owner.getId(), session.getChannelId(), session.getSessionState(), cmd,
                    session.getUserId(), session.getPlayerId());
            return;
        }
        clientCmdRouter.forward(session, cmd, body);
    }

    void prepareClientSession(NetChannel session) {
        if (session.getGate() == null) {
            session.setGate(owner.getCallPoint());
        }
    }

    void postClientChannelActive(NetChannel session) {
        owner.postCoroutine(() -> {
            owner.loginManager().initialize(session, owner.getTimeCurrent());
            LogCore.core.info("ConnService 客户端连接: service={}, sessionId={}, remote={}, loginCount={}",
                    owner.getId(), session.getChannelId(), session.getRemoteAddress(), getLoginSessionCount());
        });
    }

    void postClientChannelInactive(NetChannel session) {
        owner.postCoroutine(() -> owner.closeSession(
                session, org.evd.game.runtime.netty.BrokenType.CLIENT_CLOSE.getCode(),
                "client channel inactive"));
    }

    void postClientPacket(NetChannel session, int msgId, Chunk body) {
        owner.postCoroutine(() -> dispatchClientCmd(session, msgId, body));
    }

    public void scanHeartbeatTimeouts() {
        heartbeatScanner.scanTimeoutSessions(HEARTBEAT_TIMEOUT_MILLIS);
    }

    public boolean pushToClient(long sessionId, ClientFrameChunk packet) {
        writeClientPacket(sessionId, packet, false);
        return true;
    }

    public void pushToUserId(String userId, ClientFrameChunk packet) {
        Long sessionId = sessionRegistry.findUserSessionId(userId);
        if (sessionId != null) {
            pushToClient(sessionId, packet);
        }
    }

    public void pushToPlayerId(long playerId, ClientFrameChunk packet) {
        Long sessionId = sessionRegistry.findPlayerSessionId(playerId);
        if (sessionId != null) {
            pushToClient(sessionId, packet);
        }
    }

    public void redirectClient(long sessionId, ClientFrameChunk packet) {
        writeClientPacket(sessionId, packet, true);
    }

    public void writeClientPacket(long sessionId, ClientFrameChunk packet, boolean closeAfterWrite) {
        NetChannel channel = requireClientChannel(sessionId);
        byte[] bodyBytes;
        try {
            bodyBytes = packet.requireBodyBuffer();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("ConnService 构建客户端消息体失败: msgId=" + packet.getMsgId(), e);
        }
        ByteBuf body = Unpooled.wrappedBuffer(bodyBytes);
        int bodyLength = bodyBytes.length;
        ByteBuf head = channel.getChannel().alloc().buffer(Integer.BYTES * 2);
        head.writeInt(Integer.BYTES + bodyLength);
        head.writeInt(packet.getMsgId());
        CompositeByteBuf frame = channel.getChannel().alloc().compositeBuffer(2);
        frame.addComponents(true, head, body);
        DebugPrint.printSendClientCmd(channel, packet.getMsgId(), bodyBytes);
        if (closeAfterWrite) {
            channel.setSessionState(NetChannel.SessionState.CLOSING);
        }
        boolean accepted = closeAfterWrite ? channel.writeAndClose(frame) : channel.write(frame);
        if (!accepted) {
            throw new IllegalStateException("ConnService client channel backpressured: service="
                    + owner.getId() + ", sessionId=" + sessionId + ", bytes=" + bodyLength);
        }
        LogCore.core.debug("ConnService 回客户端: gate={}, sessionId={}, msgId={}, bytes={}, closeAfterWrite={}",
                owner.getId(), sessionId, packet.getMsgId(), bodyLength, closeAfterWrite);
    }

    public NetChannel requireClientChannel(long sessionId) {
        NetChannel channel = findClientChannel(sessionId);
        if (channel == null) {
            throw new IllegalStateException("ConnService client channel not found: service="
                    + owner.getId() + ", sessionId=" + sessionId);
        }
        return channel;
    }

    public NetChannel findClientChannel(long sessionId) {
        return channelManager.getChannel(sessionId);
    }

    public ChannelManager channelManager() {
        return channelManager;
    }

    public int getLoginSessionCount() {
        int count = 0;
        for (NetChannel channel : channelManager.getChannelMap().values()) {
            if (channel.isAuthorized()) {
                count++;
            }
        }
        return count;
    }

    ConnService owner() {
        return owner;
    }
}
