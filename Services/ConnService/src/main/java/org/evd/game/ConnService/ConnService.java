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

    public ConnService(Node node, String name, String scheduledName) {
        super(node, name, scheduledName);
    }
    public ConnService(Node node, String name, String scheduledName, int interval) {
        super(node, name, scheduledName, interval);
    }

    @Override
    public void init() {
        LogCore.core.info("ConnService Init");
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
        requireActor(ActorId.gate(session.getSessionId()), Session.class);
        LogCore.core.info("ConnService 回客户端: gate={}, sessionId={}, msgId={}, bytes={}",
                id, session.getSessionId(), msgId, body.length);
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
        registerActor(ActorId.gate(session.getSessionId()), session, ActorExecutionMode.ORDERED);
        return new ClientSessionRef(new CallPoint(node.getId(), id), session.getSessionId(), session.getSessionId());
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
}
