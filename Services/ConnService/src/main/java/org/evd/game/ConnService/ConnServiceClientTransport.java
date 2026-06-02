package org.evd.game.ConnService;

import io.netty.channel.Channel;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.ChannelManager;
import org.evd.game.runtime.netty.NetAcceptor;
import org.evd.game.runtime.netty.NetAcceptorConfig;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.support.LogCore;

import java.util.Arrays;

final class ConnServiceClientTransport {
    private final ConnService owner;
    private final ChannelManager clientChannelManager = new ChannelManager();

    private volatile NetAcceptor clientAcceptor;
    private String publicAddr;
    private int clientBossThreads = 1;
    private int clientWorkerThreads = 0;
    private int clientMaxFrameLength = 8 * 1024 * 1024;

    ConnServiceClientTransport(ConnService owner, String publicAddr) {
        this.owner = owner;
        this.publicAddr = publicAddr;
    }

    void start() {
        if (publicAddr == null || publicAddr.isBlank()) {
            LogCore.core.warn("ConnService 未配置 publicAddr，跳过 Netty 启动: service={}", owner.getId());
            return;
        }
        int split = publicAddr.lastIndexOf(':');
        String host = publicAddr.substring(0, split).trim();
        int port = Integer.parseInt(publicAddr.substring(split + 1).trim());
        clientAcceptor = new NetAcceptor(
                new NetAcceptorConfig(host, port, clientBossThreads, clientWorkerThreads),
                new ConnServiceClientChannelInitializer(this, clientMaxFrameLength));
        LogCore.core.info("ConnService Netty 启动完成: service={}, publicAddr={}", owner.getId(), publicAddr);
    }

    void shutdown() {
        NetAcceptor acceptor = clientAcceptor;
        clientAcceptor = null;
        if (acceptor != null) {
            acceptor.shutdown();
        }
        clientChannelManager.clear();
    }

    ClientSessionRef buildClientSessionRef(NetChannel session) {
        owner.registerClientSessionActor(session);
        return new ClientSessionRef(
                new CallPoint(owner.getNode().getId(), owner.getId()),
                session.getChannelId(),
                session.getChannelId());
    }

    void pushToClient(ClientSessionRef session, int msgId, Chunk body) {
        NetChannel channel = requireClientChannel(session.getSessionId());
        byte[] payload = copyChunkBody(body);
        channel.write(encodeClientPacket(msgId, payload));
        LogCore.core.info("ConnService 回客户端: gate={}, sessionId={}, msgId={}, bytes={}",
                owner.getId(), session.getSessionId(), msgId, payload.length);
    }

    NetChannel createClientSession(Channel channel) {
        return new NetChannel(channel);
    }

    void onClientChannelActive(NetChannel session) {
        clientChannelManager.addChannel(session);
        owner.post(() -> {
            owner.registerClientSessionActor(session);
            LogCore.core.info("ConnService 客户端连接: service={}, sessionId={}, remote={}",
                    owner.getId(), session.getChannelId(), session.getRemoteAddress());
        });
    }

    void onClientPacket(NetChannel session, int msgId, byte[] body) {
        owner.post(() -> owner.dispatchClientCmd(session, msgId, body));
    }

    void onClientChannelInactive(NetChannel session) {
        clientChannelManager.removeChannel(session.getChannelId());
        owner.post(() -> {
            owner.unregisterClientSessionActor(session.getChannelId());
            LogCore.core.info("ConnService 客户端断开: service={}, sessionId={}, remote={}",
                    owner.getId(), session.getChannelId(), session.getRemoteAddress());
        });
    }

    void onClientChannelException(NetChannel session, Throwable cause) {
        long sessionId = session == null ? -1L : session.getChannelId();
        LogCore.core.error("ConnService Netty 异常: service={}, sessionId={}", owner.getId(), sessionId, cause);
    }

    String getOwnerServiceId() {
        return owner.getId();
    }

    void setPublicAddr(String publicAddr) {
        this.publicAddr = publicAddr;
    }

    void setClientBossThreads(int clientBossThreads) {
        this.clientBossThreads = clientBossThreads;
    }

    void setClientWorkerThreads(int clientWorkerThreads) {
        this.clientWorkerThreads = clientWorkerThreads;
    }

    void setClientMaxFrameLength(int clientMaxFrameLength) {
        this.clientMaxFrameLength = clientMaxFrameLength;
    }

    private NetChannel requireClientChannel(long sessionId) {
        NetChannel channel = clientChannelManager.getChannel(sessionId);
        if (channel == null) {
            throw new IllegalStateException("ConnService client channel not found: service="
                    + owner.getId() + ", sessionId=" + sessionId);
        }
        return channel;
    }

    private static byte[] copyChunkBody(Chunk body) {
        return Arrays.copyOfRange(body.buffer, body.offset, body.offset + body.length);
    }

    private static byte[] encodeClientPacket(int msgId, byte[] body) {
        byte[] packet = new byte[Integer.BYTES + body.length];
        packet[0] = (byte) (msgId >>> 24);
        packet[1] = (byte) (msgId >>> 16);
        packet[2] = (byte) (msgId >>> 8);
        packet[3] = (byte) msgId;
        System.arraycopy(body, 0, packet, Integer.BYTES, body.length);
        return packet;
    }
}
