package org.evd.game.StageService;

import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Actor;
import org.evd.game.common.proxy.ConnServiceProxy;
import org.evd.game.common.proto.C2S_Login;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_Login;
import org.evd.game.runtime.Node;
import org.evd.game.annotation.Rpc;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.ClientSessionRef;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RuntimeUtils;

import java.io.IOException;
import java.util.Arrays;

@Actor
public class StageService extends Service {
    public int a;
    private Object clientCmdRegistry;
    private java.lang.reflect.Method clientCmdDispatchMethod;

    public StageService(Node node, String name, String scheduledName) {
        super(node, name, scheduledName);
    }
    public StageService(Node node, String name, String scheduledName, int interval) {
        super(node, name, scheduledName, interval);
    }

    @Rpc
    public String doSome1(int a, Integer b) {
        String str = RuntimeUtils.createStr("{}::{}::doSome1()", node.getId(), id);
        System.out.println(str);
        LogCore.core.info(str);

        ConnServiceProxy proxy = ConnServiceProxy.inst(new CallPoint("node1", "conn1"));
        String result = proxy.con();
        System.out.println("receive = " + result);

        return str;

    }

    @Rpc
    public void doSome2(Integer a, Integer b) {
        LogCore.core.info("StageService doSome2()");
    }

    @Rpc
    public String doSome3(Integer a) {
        System.out.println("StageService doSome3()");
//        LogCore.core.info("StageService doSome3()");
        return "from StageService doSome3";
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

    @ClientCmd(MsgId.C2S_LOGIN_VALUE)
    public void login(ClientSessionRef session, C2S_Login req) {
        LogCore.core.info("StageService 收到客户端登录: service={}, sessionId={}, account={}", id, session.getSessionId(), req.getAccount());

        S2C_Login resp = S2C_Login.newBuilder()
                .setRoleId(session.getSessionId())
                .setToken("token-" + req.getAccount())
                .build();
        ConnServiceProxy.inst(session.getGate()).pushToClient(session, MsgId.S2C_LOGIN_VALUE, new Chunk(resp));
    }

    private Object clientCmdRegistry() {
        if (clientCmdRegistry == null) {
            try {
                Class<?> registryClass = Class.forName("org.evd.game.StageService.StageServiceClientCmdRegistry");
                clientCmdRegistry = registryClass.getConstructor(StageService.class).newInstance(this);
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
