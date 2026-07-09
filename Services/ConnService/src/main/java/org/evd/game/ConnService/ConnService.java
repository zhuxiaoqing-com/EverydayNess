package org.evd.game.ConnService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proto.C2S_ConnPing;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_ConnPing;
import org.evd.game.common.proxy.LobbyService.LobbyOfflineActorProxy;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.netty.BaseChannelInitializer;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.ChannelManager;
import org.evd.game.runtime.netty.NetAcceptor;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.support.LogCore;

public class ConnService extends Service {
    private static final long HEARTBEAT_SCAN_INTERVAL_MILLIS = 5_000L;
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 60_000L;

    private final ConnServiceClientCmdRouter clientCmdRouter;
    private final ChannelManager clientChannelManager;
    private final ConnServiceHeartbeatScanner heartbeatScanner;

    private volatile NetAcceptor clientAcceptor;

    public ConnService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.clientCmdRouter = new ConnServiceClientCmdRouter(this);
        this.clientChannelManager = new ChannelManager();
        this.heartbeatScanner = new ConnServiceHeartbeatScanner(this, clientChannelManager);
    }

    @Override
    public void init() {
        super.init();
        LogCore.core.info("ConnService Init");
        int port = getServiceInfo().getAddressInfo().getPort();
        clientAcceptor = new NetAcceptor(port,
                new BaseChannelInitializer(() -> new ConnServiceClientChannelHandler(clientChannelManager, this), true));
        LogCore.core.info("ConnService Netty 启动完成: service={}, port={}", id, port);
        newRepeatedTimer(HEARTBEAT_SCAN_INTERVAL_MILLIS, false, this::scanHeartbeatTimeouts);
    }

    public void dispatchClientCmd(NetChannel session, int cmd, Chunk body) {
        if (!session.canProcessClientCmd(cmd)) {
            LogCore.core.warn("ConnService 拒绝非法阶段协议: service={}, sessionId={}, state={}, cmdId={}, userId={}, playerId={}",
                    id, session.getChannelId(), session.getSessionState(), cmd, session.getUserId(), session.getPlayerId());
            return;
        }
        clientCmdRouter.forward(session, cmd, body);
    }

    @Rpc
    public void pushToClient(long sessionId, ClientFrameChunk packet) {
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
        channel.write(frame);
        LogCore.core.info("ConnService 回客户端: gate={}, sessionId={}, bytes={}",
                id, sessionId, bodyLength);
    }

    @Rpc
    public String getPublicAddr() {
        return serviceInfo == null ? "" : serviceInfo.getPublicAddr();
    }

    @Rpc
    public int getLoginSessionCount() {
        return countAuthorizedSessions();
    }

    @Rpc
    public boolean confirmLogin(long sessionId, String userId, long playerId) {
        NetChannel session = findClientChannel(sessionId);
        if (session == null) {
            LogCore.core.warn("ConnService 登录确认失败，session 不存在: service={}, sessionId={}", id, sessionId);
            return false;
        }
        session.setAuthorized(true);
        session.setUserId(userId);
        session.setPlayerId(playerId);
        session.setSessionState(playerId > 0L
                ? NetChannel.SessionState.LOGIN_READY
                : NetChannel.SessionState.SELECT_ROLE_READY);
        session.setLastPingTime(getTimeCurrent());
        session.setBrokenType(BrokenType.NONE);
        LogCore.core.info("ConnService 登录确认成功: service={}, sessionId={}, userId={}, playerId={}, state={}",
                id, sessionId, userId, playerId, session.getSessionState());
        return true;
    }

    @Rpc
    public boolean updatePlayerBinding(long sessionId, long playerId) {
        NetChannel session = findClientChannel(sessionId);
        if (session == null) {
            return false;
        }
        session.setPlayerId(playerId);
        session.setSessionState(playerId > 0L
                ? NetChannel.SessionState.LOGIN_READY
                : NetChannel.SessionState.SELECT_ROLE_READY);
        return true;
    }

    @Rpc
    public boolean kickSession(long sessionId, int brokenTypeCode, String reason) {
        BrokenType brokenType = BrokenType.fromCode(brokenTypeCode);
        return closeSession(sessionId, brokenType, reason);
    }

    @ClientCmd(MsgId.C2S_CONN_PING_VALUE)
    public void onConnPing(ClientSessionRef session, C2S_ConnPing req) {
        NetChannel channel = findClientChannel(session.getSessionId());
        if (channel != null) {
            channel.setLastPingTime(getTimeCurrent());
        }

        S2C_ConnPing resp = S2C_ConnPing.newBuilder()
                .setClientTime(req.getClientTime())
                .setServerTime(getTime())
                .build();
        pushToClient(session.getSessionId(), ClientFrameChunk.wrap(MsgId.S2C_CONN_PING_VALUE, resp));
    }

    ClientSessionRef buildClientSessionRef(NetChannel session) {
        registerClientSessionActor(session);
        ClientSessionRef sessionRef = session.getSessionRef();
        if (sessionRef.getGate() == null) {
            sessionRef.setGate(new CallPoint(node.getId(), id));
        }
        return sessionRef;
    }

    @Override
    public void onClose() {
        NetAcceptor acceptor = clientAcceptor;
        clientAcceptor = null;
        if (acceptor != null) {
            acceptor.shutdown();
        }
        clientChannelManager.clear();
        super.onClose();
    }

    void onClientChannelActive(NetChannel session) {
        long sessionId = session.getChannelId();
        session.setAuthorized(false);
        session.setUserId("");
        session.setPlayerId(0L);
        session.setSessionState(NetChannel.SessionState.CONNECTED);
        session.setLastPingTime(getTimeCurrent());
        registerClientSessionActor(session);
        LogCore.core.info("ConnService 客户端连接: service={}, sessionId={}, remote={},  loginCount={}",
                id, sessionId, session.getRemoteAddress(), countAuthorizedSessions());
    }

    void onClientChannelInactive(NetChannel session) {
        long sessionId = session.getChannelId();
        ClientSessionRef sessionRef = session.getSessionRef();
        unregisterClientSessionActor(sessionId);
        LogCore.core.info("ConnService 客户端断开: service={}, sessionId={}, remote={}, brokenType={},  loginCount={}",
                id, sessionId, session.getRemoteAddress(), session.getBrokenType(),
                countAuthorizedSessions());
        if (!sessionRef.isAuthorized() || sessionRef.getUserId().isBlank()) {
            return;
        }
        CallPoint lobbyRemote = getLobbyRemote();
        if (lobbyRemote == null) {
            LogCore.core.warn("ConnService 未找到 LobbyService，跳过离线流程: service={}, sessionId={}", id, sessionId);
            return;
        }
        LobbyOfflineActorProxy.inst().onSessionOffline(
                lobbyRemote,
                sessionRef.getUserId(),
                sessionRef.getPlayerId(),
                copyCallPoint(),
                sessionId,
                session.getBrokenTypeCode()
        );
    }


    void registerClientSessionActor(NetChannel session) {
        ActorId actorId = ActorId.gate(session.getChannelId());
        if (!hasActor(actorId)) {
            registerActor(actorId, MailBoxType.UNORDERED);
        }
    }

    void unregisterClientSessionActor(long sessionId) {
        unregisterActor(ActorId.gate(sessionId));
    }

    private CallPoint getLobbyRemote() {
        return node.getAnyCallPointByType(ServiceType.LOBBY);
    }

    private void scanHeartbeatTimeouts() {
        heartbeatScanner.scanTimeoutSessions(HEARTBEAT_TIMEOUT_MILLIS);
    }

    boolean closeSession(long sessionId, BrokenType brokenType, String reason) {
        NetChannel session = findClientChannel(sessionId);
        if (session == null) {
            return false;
        }
        session.setBrokenType(brokenType);
        LogCore.core.info("ConnService 关闭连接: service={}, sessionId={}, brokenType={}, reason={}",
                id, sessionId, brokenType, reason);
        session.close();
        return true;
    }

    void postClientChannelActive(NetChannel session) {
        postCoroutine(() -> onClientChannelActive(session));
    }

    void postClientPacket(NetChannel session, int msgId, Chunk body) {
        postCoroutine(() -> dispatchClientCmd(session, msgId, body));
    }

    void postClientChannelInactive(NetChannel session) {
        postCoroutine(() -> onClientChannelInactive(session));
    }

    private NetChannel requireClientChannel(long sessionId) {
        NetChannel channel = findClientChannel(sessionId);
        if (channel == null) {
            throw new IllegalStateException("ConnService client channel not found: service="
                    + id + ", sessionId=" + sessionId);
        }
        return channel;
    }

    private NetChannel findClientChannel(long sessionId) {
        return clientChannelManager.getChannel(sessionId);
    }

    private int countAuthorizedSessions() {
        int count = 0;
        for (NetChannel channel : clientChannelManager.snapshotChannels()) {
            if (channel.getSessionState() == NetChannel.SessionState.LOGIN_READY) {
                count++;
            }
        }
        return count;
    }

}
