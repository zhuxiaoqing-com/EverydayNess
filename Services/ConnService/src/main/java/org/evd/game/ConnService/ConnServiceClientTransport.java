package org.evd.game.ConnService;

import io.netty.buffer.ByteBuf;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.*;
import org.evd.game.runtime.support.LogCore;

import java.util.Arrays;

final class ConnServiceClientTransport {
    public final ConnService owner;
    private final ChannelManager clientChannelManager;

    private volatile NetAcceptor clientAcceptor;

    ConnServiceClientTransport(ConnService owner, ChannelManager clientChannelManager) {
        this.owner = owner;
        this.clientChannelManager = clientChannelManager;
    }

    void start() {
        AddressInfo addressInfo = owner.getServiceInfo().getAddressInfo();
        clientAcceptor = new NetAcceptor(owner.getServiceInfo().getAddressInfo().getPort(),
                new BaseChannelInitializer(new ConnServiceClientChannelHandler(clientChannelManager,this), true));
        LogCore.core.info("ConnService Netty 启动完成: service={}, port={}", owner.getId(), addressInfo.getPort());
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
        ClientSessionRef sessionRef = session.getSessionRef();
        if (sessionRef.getGate() == null) {
            sessionRef.setGate(new CallPoint(owner.getNode().getId(), owner.getId()));
        }
        return sessionRef;
    }

    void pushToClient(long sessionId, int msgId, Chunk body) {
        NetChannel channel = requireClientChannel(sessionId);
        byte[] payload = copyChunkBody(body);
        channel.write(encodeClientPacket(msgId, payload));
        LogCore.core.info("ConnService 回客户端: gate={}, sessionId={}, msgId={}, bytes={}",
                owner.getId(), sessionId, msgId, payload.length);
    }

    void onClientChannelActive(NetChannel session) {
        owner.post(() -> owner.onClientChannelActive(session));
    }

    void onClientPacket(NetChannel session, int msgId, Chunk body) {
        owner.post(() -> owner.dispatchClientCmd(session, msgId, body));
    }

    void onClientChannelInactive(NetChannel session) {
        owner.post(() -> owner.onClientChannelInactive(session));
    }

    void onClientChannelException(NetChannel session, Throwable cause) {
        owner.post(() -> owner.onClientChannelException(session, cause));
    }

    String getOwnerServiceId() {
        return owner.getId();
    }


    private NetChannel requireClientChannel(long sessionId) {
        NetChannel channel = clientChannelManager.getChannel(sessionId);
        if (channel == null) {
            throw new IllegalStateException("ConnService client channel not found: service="
                    + owner.getId() + ", sessionId=" + sessionId);
        }
        return channel;
    }

    NetChannel findClientChannel(long sessionId) {
        return clientChannelManager.getChannel(sessionId);
    }

    int countAuthorizedSessions() {
        int count = 0;
        for (NetChannel channel : clientChannelManager.snapshotChannels()) {
            if (channel.getSessionState() == NetChannel.SessionState.LOGIN_READY) {
                count++;
            }
        }
        return count;
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
