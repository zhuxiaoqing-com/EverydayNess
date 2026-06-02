package org.evd.game.ConnService;

import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.client.ClientSessionRef;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

final class ConnServiceClientCommandDispatcher {
    private final ConnService owner;
    private Object clientCmdRegistry;
    private Method clientCmdDispatchMethod;

    ConnServiceClientCommandDispatcher(ConnService owner) {
        this.owner = owner;
    }

    void dispatch(ClientSessionRef session, int msgId, Chunk body) {
        try {
            clientCmdDispatchMethod().invoke(clientCmdRegistry(), session, msgId, copyChunkBody(body));
        } catch (ReflectiveOperationException e) {
            Throwable cause = unwrapCause(e);
            if (cause instanceof IOException ioException) {
                throw new RuntimeException("客户端协议解析失败: msgId=" + msgId + ", service=" + owner.getId(), ioException);
            }
            throw new RuntimeException("客户端协议分发失败: msgId=" + msgId + ", service=" + owner.getId(), cause);
        }
    }

    private Object clientCmdRegistry() {
        if (clientCmdRegistry == null) {
            try {
                Class<?> registryClass = Class.forName("org.evd.game.ConnService.ConnServiceClientCmdRegistry");
                clientCmdRegistry = registryClass.getConstructor(ConnService.class).newInstance(owner);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("初始化客户端协议分发表失败: service=" + owner.getId(), e);
            }
        }
        return clientCmdRegistry;
    }

    private Method clientCmdDispatchMethod() {
        if (clientCmdDispatchMethod == null) {
            try {
                clientCmdDispatchMethod = clientCmdRegistry().getClass()
                        .getMethod("dispatch", ClientSessionRef.class, int.class, byte[].class);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("初始化客户端协议分发方法失败: service=" + owner.getId(), e);
            }
        }
        return clientCmdDispatchMethod;
    }

    private static Throwable unwrapCause(ReflectiveOperationException e) {
        if (e instanceof InvocationTargetException invocationTargetException) {
            return invocationTargetException.getCause();
        }
        return e;
    }

    private static byte[] copyChunkBody(Chunk body) {
        return Arrays.copyOfRange(body.buffer, body.offset, body.offset + body.length);
    }
}
