package org.evd.game.ConnService;

import io.netty.channel.Channel;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.common.proto.C2S_ConnPing;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_ConnPing;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.ClientSessionRef;
import org.evd.game.common.proxy.StageServiceProxy;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorExecutionMode;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.netty.ChannelManager;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.netty.NetAcceptor;
import org.evd.game.runtime.netty.NetAcceptorConfig;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RuntimeUtils;

import java.io.IOException;
import java.util.Arrays;

@Actor
public class ConnService extends Service {
    boolean first = true;
    private Object clientCmdRegistry;
    private java.lang.reflect.Method clientCmdDispatchMethod;
    private final ChannelManager clientChannelManager = new ChannelManager();
    private volatile NetAcceptor clientAcceptor;
    private String publicAddr;
    private int clientBossThreads = 1;
    private int clientWorkerThreads = 0;
    private int clientMaxFrameLength = 8 * 1024 * 1024;

    public ConnService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.publicAddr = serviceInfo.getPublicAddr();
    }

    @Override
    public void init() {
        LogCore.core.info("ConnService Init");
        startClientTransport();
    }

    @Override
    public void tick() {
//        StageServiceProxy.inst().doSome1(1, 2);
//        if (first){
            first = false;
            CallPoint callPoint = new CallPoint("node2", "stage1");
            String s = StageServiceProxy.doSome1(callPoint, 1, 2);
            System.out.println("receive = " + s);
//            LogCore.core.info("ConnService tick reveive {" + s + "}");
//        }

    }

    @Rpc
    public String con(){
        String str = RuntimeUtils.createStr("{}::{}::con()", node.getId(), id);
        System.out.println(str);
        return str;
    }

    @Rpc
    public void con1(){

    }

    @Rpc
    public void con4(){

    }

    public void dispatchClientCmd(NetChannel session, int cmd, byte[] body) {
        ConnServiceClientCmdRouter.forward(this, session, cmd, body);
    }

    @Rpc
    public void forwardClientCmd(ClientSessionRef session, int msgId, Chunk body) {
        try {
            clientCmdDispatchMethod().invoke(clientCmdRegistry(), session, msgId, copyChunkBody(body));
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException invocationTargetException
                    ? invocationTargetException.getCause()
                    : e;
            if (cause instanceof IOException ioException) {
                throw new RuntimeException("客户端协议解析失败: msgId=" + msgId + ", service=" + id, ioException);
            }
            throw new RuntimeException("客户端协议分发失败: msgId=" + msgId + ", service=" + id, cause);
        }
    }

    @Rpc
    public void pushToClient(ClientSessionRef session, int msgId, Chunk body) {
        requireActor(gateActorId(session.getSessionId()), NetChannel.class);
        NetChannel channel = requireClientChannel(session.getSessionId());
        byte[] payload = copyChunkBody(body);
        channel.write(encodeClientPacket(msgId, payload));
        LogCore.core.info("ConnService 回客户端: gate={}, sessionId={}, msgId={}, bytes={}",
                id, session.getSessionId(), msgId, payload.length);
    }

    @ClientCmd(MsgId.C2S_CONN_PING_VALUE)
    public void onConnPing(ClientSessionRef session, C2S_ConnPing req) {
        LogCore.core.info("ConnService 收到客户端 Ping: service={}, sessionId={}, text={}",
                id, session.getSessionId(), req.getText());

        S2C_ConnPing resp = S2C_ConnPing.newBuilder()
                .setText("pong-" + req.getText())
                .build();
        pushToClient(session, MsgId.S2C_CONN_PING_VALUE, new Chunk(resp));
    }

    ClientSessionRef buildClientSessionRef(NetChannel session) {
        ensureSessionActorRegistered(session);
        return new ClientSessionRef(new CallPoint(node.getId(), id), session.getChannelId(), session.getChannelId());
    }

    public void setPublicAddr(String publicAddr) {
        this.publicAddr = publicAddr;
    }

    public void setClientBossThreads(int clientBossThreads) {
        this.clientBossThreads = clientBossThreads;
    }

    public void setClientWorkerThreads(int clientWorkerThreads) {
        this.clientWorkerThreads = clientWorkerThreads;
    }

    public void setClientMaxFrameLength(int clientMaxFrameLength) {
        this.clientMaxFrameLength = clientMaxFrameLength;
    }

    private Object clientCmdRegistry() {
        if (clientCmdRegistry == null) {
            try {
                Class<?> registryClass = Class.forName("org.evd.game.ConnService.ConnServiceClientCmdRegistry");
                clientCmdRegistry = registryClass.getConstructor(ConnService.class).newInstance(this);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("初始化客户端协议分发表失败: service=" + id, e);
            }
        }
        return clientCmdRegistry;
    }

    private java.lang.reflect.Method clientCmdDispatchMethod() {
        if (clientCmdDispatchMethod == null) {
            try {
                clientCmdDispatchMethod = clientCmdRegistry().getClass()
                        .getMethod("dispatch", ClientSessionRef.class, int.class, byte[].class);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("初始化客户端协议分发方法失败: service=" + id, e);
            }
        }
        return clientCmdDispatchMethod;
    }

    private byte[] copyChunkBody(Chunk body) {
        return Arrays.copyOfRange(body.buffer, body.offset, body.offset + body.length);
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

    private void startClientTransport() {
        if (publicAddr == null || publicAddr.isBlank()) {
            LogCore.core.warn("ConnService 未配置 publicAddr，跳过 Netty 启动: service={}", id);
            return;
        }
        int split = publicAddr.lastIndexOf(':');
        String host = publicAddr.substring(0, split).trim();
        int port = Integer.parseInt(publicAddr.substring(split + 1).trim());
        clientAcceptor = new NetAcceptor(new NetAcceptorConfig(
                host,
                port,
                clientBossThreads,
                clientWorkerThreads),
                new ConnServiceClientChannelInitializer(this, clientMaxFrameLength));
        LogCore.core.info("ConnService Netty 启动完成: service={}, publicAddr={}", id, publicAddr);
    }

    private void handleClientConnected(NetChannel session) {
        ensureSessionActorRegistered(session);
        LogCore.core.info("ConnService 客户端连接: service={}, sessionId={}, remote={}",
                id, session.getChannelId(), session.getRemoteAddress());
    }

    private void handleClientDisconnected(NetChannel session) {
        unregisterActor(gateActorId(session.getChannelId()));
        LogCore.core.info("ConnService 客户端断开: service={}, sessionId={}, remote={}",
                id, session.getChannelId(), session.getRemoteAddress());
    }

    private void ensureSessionActorRegistered(NetChannel session) {
        ActorId actorId = gateActorId(session.getChannelId());
        if (!hasActor(actorId)) {
            registerActor(actorId, session, ActorExecutionMode.ORDERED);
        }
    }

    private ActorId gateActorId(long sessionId) {
        return ActorId.gate(sessionId);
    }

    private NetChannel requireClientChannel(long sessionId) {
        NetChannel channel = clientChannelManager.getChannel(sessionId);
        if (channel == null) {
            throw new IllegalStateException("ConnService client channel not found: service=" + id + ", sessionId=" + sessionId);
        }
        return channel;
    }

    private byte[] encodeClientPacket(int msgId, byte[] body) {
        byte[] packet = new byte[Integer.BYTES + body.length];
        packet[0] = (byte) (msgId >>> 24);
        packet[1] = (byte) (msgId >>> 16);
        packet[2] = (byte) (msgId >>> 8);
        packet[3] = (byte) msgId;
        System.arraycopy(body, 0, packet, Integer.BYTES, body.length);
        return packet;
    }

    NetChannel createClientSession(Channel channel) {
        return new NetChannel(channel);
    }

    void onClientChannelActive(NetChannel session, Channel channel) {
        clientChannelManager.addChannel(session);
        post(() -> handleClientConnected(session));
    }

    void onClientPacket(NetChannel session, int msgId, byte[] body) {
        post(() -> dispatchClientCmd(session, msgId, body));
    }

    void onClientChannelInactive(NetChannel session) {
        clientChannelManager.removeChannel(session.getChannelId());
        post(() -> handleClientDisconnected(session));
    }

    void onClientChannelException(NetChannel session, Throwable cause) {
        long sessionId = session == null ? -1L : session.getChannelId();
        LogCore.core.error("ConnService Netty 异常: service={}, sessionId={}", id, sessionId, cause);
    }
}
