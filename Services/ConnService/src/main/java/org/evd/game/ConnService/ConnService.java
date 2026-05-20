package org.evd.game.ConnService;

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
import org.evd.game.runtime.client.ClientTransport;
import org.evd.game.runtime.client.ClientTransportHandler;
import org.evd.game.runtime.client.NettyClientTransport;
import org.evd.game.runtime.client.NettyServerConfig;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Session;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RuntimeUtils;

import java.io.IOException;
import java.util.Arrays;

@Actor
public class ConnService extends Service {
    boolean first = true;
    private Object clientCmdRegistry;
    private java.lang.reflect.Method clientCmdDispatchMethod;
    private volatile ClientTransport clientTransport;
    private String clientHost = "0.0.0.0";
    private int clientPort = -1;
    private int clientBossThreads = 1;
    private int clientWorkerThreads = 0;
    private int clientMaxFrameLength = 8 * 1024 * 1024;

    public ConnService(Node node, String name, String scheduledName) {
        super(node, name, scheduledName);
    }
    public ConnService(Node node, String name, String scheduledName, int interval) {
        super(node, name, scheduledName, interval);
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

    public void dispatchClientCmd(Session session, int cmd, byte[] body) {
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
        requireActor(gateActorId(session.getSessionId()), Session.class);
        ClientTransport transport = requireClientTransport();
        byte[] payload = copyChunkBody(body);
        transport.send(session.getSessionId(), msgId, payload);
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

    ClientSessionRef buildClientSessionRef(Session session) {
        ensureSessionActorRegistered(session);
        return new ClientSessionRef(new CallPoint(node.getId(), id), session.getSessionId(), session.getSessionId());
    }

    public void setClientHost(String clientHost) {
        this.clientHost = clientHost;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
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
        ClientTransport transport = clientTransport;
        clientTransport = null;
        if (transport != null) {
            transport.stop();
        }
        super.onClose();
    }

    private void startClientTransport() {
        if (clientPort <= 0) {
            LogCore.core.warn("ConnService 未配置客户端监听端口，跳过 Netty 启动: service={}", id);
            return;
        }
        NettyServerConfig config = new NettyServerConfig(
                clientHost,
                clientPort,
                clientBossThreads,
                clientWorkerThreads,
                clientMaxFrameLength);
        NettyClientTransport transport = new NettyClientTransport(config, new ClientTransportHandler() {
            @Override
            public void onConnected(Session session) {
                post(() -> handleClientConnected(session));
            }

            @Override
            public void onDisconnected(Session session) {
                post(() -> handleClientDisconnected(session));
            }

            @Override
            public void onPacket(Session session, int msgId, byte[] body) {
                post(() -> dispatchClientCmd(session, msgId, body));
            }

            @Override
            public void onException(Session session, Throwable cause) {
                long sessionId = session == null ? -1L : session.getSessionId();
                LogCore.core.error("ConnService Netty 异常: service={}, sessionId={}", id, sessionId, cause);
            }
        });
        transport.start();
        clientTransport = transport;
        LogCore.core.info("ConnService Netty 启动完成: service={}, host={}, port={}", id, clientHost, clientPort);
    }

    private void handleClientConnected(Session session) {
        ensureSessionActorRegistered(session);
        LogCore.core.info("ConnService 客户端连接: service={}, sessionId={}, remote={}",
                id, session.getSessionId(), session.getRemoteAddress());
    }

    private void handleClientDisconnected(Session session) {
        unregisterActor(gateActorId(session.getSessionId()));
        LogCore.core.info("ConnService 客户端断开: service={}, sessionId={}, remote={}",
                id, session.getSessionId(), session.getRemoteAddress());
    }

    private void ensureSessionActorRegistered(Session session) {
        ActorId actorId = gateActorId(session.getSessionId());
        if (!hasActor(actorId)) {
            registerActor(actorId, session, ActorExecutionMode.ORDERED);
        }
    }

    private ActorId gateActorId(long sessionId) {
        return ActorId.gate(sessionId);
    }

    private ClientTransport requireClientTransport() {
        ClientTransport transport = clientTransport;
        if (transport == null) {
            throw new IllegalStateException("ConnService client transport not started: service=" + id);
        }
        return transport;
    }
}
