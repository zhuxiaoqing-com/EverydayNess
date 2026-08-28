package org.evd.game.ConnService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proto.C2S_ConnPing;
import org.evd.game.common.proto.C2S_CreateRole;
import org.evd.game.common.proto.C2S_SelectRoleEnter;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_ConnPing;
import org.evd.game.common.proxy.LobbyService.LobbyRoleRpcProxy;
import org.evd.game.common.proxy.OnlineService.OnlinePlayerLoginRpcProxy;
import org.evd.game.ConnService.login.ConnLoginManager;
import org.evd.game.ConnService.offline.ConnOfflineManager;
import org.evd.game.ConnService.reconcile.GwOnlineReconcileS;
import org.evd.game.ConnService.session.ConnSessionRegistry;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.netty.BaseChannelInitializer;
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
    private final ConnSessionRegistry sessionRegistry;
    private final ConnLoginManager loginManager;
    private final ConnOfflineManager offlineManager;
    private final GwOnlineReconcileS gwOnlineReconcileS;

    private volatile NetAcceptor clientAcceptor;

    public ConnService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.clientCmdRouter = new ConnServiceClientCmdRouter(this);
        this.clientChannelManager = new ChannelManager();
        this.heartbeatScanner = new ConnServiceHeartbeatScanner(this, clientChannelManager);
        this.sessionRegistry = new ConnSessionRegistry();
        this.loginManager = new ConnLoginManager(this, sessionRegistry);
        this.offlineManager = new ConnOfflineManager(this, sessionRegistry);
        this.gwOnlineReconcileS = new GwOnlineReconcileS(this);
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
        newRepeatedTimerCoroutine(GwOnlineReconcileS.INTERVAL_MILLIS, false, gwOnlineReconcileS::reconcile);
    }

    public void dispatchClientCmd(NetChannel session, int cmd, Chunk body) {
        if (!session.canProcessClientCmd(cmd)) {
            LogCore.core.warn("ConnService 拒绝非法阶段协议: service={}, sessionId={}, state={}, cmdId={}, userId={}, playerId={}",
                    id, session.getChannelId(), session.getSessionState(), cmd, session.getUserId(), session.getPlayerId());
            return;
        }
        clientCmdRouter.forward(session, cmd, body);
    }

    /** Conn 从真实连接补充用户 ID，再交给 LobbyService 创建角色。 */
    public void createRole(ClientSessionRef session, C2S_CreateRole request) {
        NetChannel channel = requireClientChannel(session.getSessionId());
        String userId = channel.getUserId();
        if (userId.isBlank()) {
            throw new IllegalStateException("ConnService 创角请求没有已登录用户: sessionId="
                    + session.getSessionId());
        }
        CallPoint lobby = getNode().getAnyCallPointByType(ServiceType.LOBBY);
        if (lobby == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: service=LobbyService, msgId="
                    + MsgId.C2S_CREATE_ROLE_VALUE);
        }
        C2S_CreateRole forwarded = request.toBuilder().setUserId(userId).build();
        LobbyRoleRpcProxy.sendCreateRole(lobby, session, forwarded);
    }

    /** Conn 从真实连接补充用户 ID，再交给 OnlineService 选角登录。 */
    public void selectRoleEnter(ClientSessionRef session, C2S_SelectRoleEnter request) {
        NetChannel channel = requireClientChannel(session.getSessionId());
        String userId = channel.getUserId();
        if (userId.isBlank()) {
            throw new IllegalStateException("ConnService 选角请求没有已登录用户: sessionId="
                    + session.getSessionId());
        }
        CallPoint online = getNode().getAnyCallPointByType(ServiceType.ONLINE);
        if (online == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: service=OnlineService, msgId="
                    + MsgId.C2S_SELECT_ROLE_ENTER_VALUE);
        }
        C2S_SelectRoleEnter forwarded = request.toBuilder().setUserId(userId).build();
        OnlinePlayerLoginRpcProxy.sendSelectRoleEnter(online, session, forwarded);
    }

    public void pushToClient(long sessionId, ClientFrameChunk packet) {
        writeClientPacket(sessionId, packet, false);
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
                    + id + ", sessionId=" + sessionId + ", bytes=" + bodyLength);
        }
        LogCore.core.debug("ConnService 回客户端: gate={}, sessionId={}, msgId={}, bytes={}, closeAfterWrite={}",
                id, sessionId, packet.getMsgId(), bodyLength, closeAfterWrite);
    }

    public String getPublicAddr() {
        return serviceInfo == null ? "" : serviceInfo.getPublicAddr();
    }

    /** 返回当前已授权登录会话数量，供 OnlineService 进行负载选择。 */
    public int getLoginSessionCount() {
        return countAuthorizedSessions();
    }

    /** 在 GW 注册玩家 mailbox，并将其 ActorAddress 发布到全局 LocationService。 */
    public ActorAddress registerPlayerMailbox(long playerId) {
        if (playerId <= 0L) {
            return null;
        }
        ActorId actorId = ActorId.gate(playerId);
        if (!hasActor(actorId)) {
            registerActor(actorId, MailBoxType.UNORDERED);
            LogCore.core.info("ConnService 玩家 GW mailbox 注册成功: service={}, playerId={}, actorAddress={}",
                    id, playerId, getActorAddress(actorId));
        }
        return getActorAddress(actorId);
    }

    public void removePlayerActorAddress(long playerId) {
        if (playerId > 0L) {
            ActorId playerActorId = ActorId.player(playerId);
            getMessageLocationSender().remove(playerActorId);
            LogCore.core.info("ConnService 删除 PlayerActorAddress 缓存: playerId={}, playerActorId={}",
                    playerId, playerActorId);
            ActorId actorId = ActorId.gate(playerId);
            if (hasActor(actorId)) {
                ActorAddress actorAddress = getActorAddress(actorId);
                unregisterActor(actorId);
                LogCore.core.info("ConnService 删除玩家 GW ActorAddress: service={}, playerId={}, actorAddress={}",
                        id, playerId, actorAddress);
            }
        }
    }

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

    void prepareClientSession(NetChannel session) {
        if (session.getGate() == null) {
            session.setGate(getCallPoint());
        }
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
        loginManager.initialize(session, getTimeCurrent());
        LogCore.core.info("ConnService 客户端连接: service={}, sessionId={}, remote={},  loginCount={}",
                id, sessionId, session.getRemoteAddress(), countAuthorizedSessions());
    }

    private void scanHeartbeatTimeouts() {
        heartbeatScanner.scanTimeoutSessions(HEARTBEAT_TIMEOUT_MILLIS);
    }

    /**
     * 将 GW 当前持有的玩家连接交给 Online 校验；返回结果只允许关闭原 session 代次。
     */
    /** 统一执行网关连接关闭、离线通知和资源清理。 */
    public void closeSession(long sessionId, int brokenTypeCode, String reason) {
        offlineManager.closeSession(sessionId, brokenTypeCode, reason);
    }

    /** 返回登录流程管理器，供 Conn 登录 Actor 委托业务处理。 */
    public ConnLoginManager loginManager() {
        return loginManager;
    }

    /** 返回离线流程管理器，供 Conn 离线 Actor 委托业务处理。 */
    public ConnOfflineManager offlineManager() {
        return offlineManager;
    }

    /** 在 ConnService 协程上下文中统一处理指定连接的关闭清理。 */
    public void closeSession(NetChannel session, int brokenTypeCode, String reason) {
        offlineManager.closeSession(session, brokenTypeCode, reason);
    }

    void postClientChannelActive(NetChannel session) {
        postCoroutine(() -> onClientChannelActive(session));
    }

    void postClientPacket(NetChannel session, int msgId, Chunk body) {
        postCoroutine(() -> dispatchClientCmd(session, msgId, body));
    }

    private NetChannel requireClientChannel(long sessionId) {
        NetChannel channel = findClientChannel(sessionId);
        if (channel == null) {
            throw new IllegalStateException("ConnService client channel not found: service="
                    + id + ", sessionId=" + sessionId);
        }
        return channel;
    }

    public NetChannel findClientChannel(long sessionId) {
        return clientChannelManager.getChannel(sessionId);
    }

    public ChannelManager clientChannelManager() {
        return clientChannelManager;
    }

    /** 遍历网关连接并统计已完成授权的会话数量。 */
    private int countAuthorizedSessions() {
        int count = 0;
        for (NetChannel channel : clientChannelManager.getChannelMap().values()) {
            if (channel.isAuthorized()) {
                count++;
            }
        }
        return count;
    }

}
