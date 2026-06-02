package org.evd.game.runtime.client;

import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.DistributeConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientCmdRouteTable {
    private final Map<Integer, RouteEntry> routes = new LinkedHashMap<>();

    public void register(int msgId, String serviceClassName, String proxyClassName) {
        RouteEntry routeEntry = new RouteEntry(serviceClassName, resolveForwardMethod(proxyClassName));
        RouteEntry previous = routes.putIfAbsent(msgId, routeEntry);
        if (previous != null) {
            throw new IllegalStateException("客户端协议重复注册: msgId=" + msgId
                    + ", service=" + previous.serviceClassName
                    + ", service=" + serviceClassName);
        }
    }

    public void forward(ClientSessionRef session, int msgId, byte[] body) {
        RouteEntry routeEntry = routes.get(msgId);
        if (routeEntry == null) {
            throw new IllegalStateException("未注册的客户端协议: msgId=" + msgId);
        }
        CallPoint callPoint = DistributeConfig.getNodeByServiceClass(routeEntry.serviceClassName, session.getRouteKey());
        if (callPoint == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: msgId=" + msgId
                    + ", service=" + routeEntry.serviceClassName);
        }
        routeEntry.forward(callPoint, session, msgId, body);
    }

    private static Method resolveForwardMethod(String proxyClassName) {
        try {
            return Class.forName(proxyClassName)
                    .getMethod("forwardClientCmd", CallPoint.class, ClientSessionRef.class, int.class, Chunk.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("初始化客户端协议转发方法失败: proxy=" + proxyClassName, e);
        }
    }

    private record RouteEntry(String serviceClassName, Method forwardMethod) {
        private void forward(CallPoint callPoint, ClientSessionRef session, int msgId, byte[] body) {
            try {
                forwardMethod.invoke(null, callPoint, session, msgId, new Chunk(body));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("调用客户端协议转发方法失败: service=" + serviceClassName, e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw new RuntimeException("转发客户端协议失败: msgId=" + msgId
                        + ", service=" + serviceClassName, cause);
            }
        }
    }
}
