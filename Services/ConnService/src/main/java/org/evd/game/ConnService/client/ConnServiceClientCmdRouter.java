package org.evd.game.ConnService.client;

import org.evd.game.annotation.service.ServiceName;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.client.ClientCmdRouteTable;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.NetChannel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class ConnServiceClientCmdRouter {
    private static final String ROUTE_REGISTRY_SUFFIX = "ClientCmdRouteRegistry";
    private static final String REGISTER_METHOD_NAME = "register";

    private final ConnClientConnection connection;
    private final ClientCmdRouteTable routeTable = new ClientCmdRouteTable();

    ConnServiceClientCmdRouter(ConnClientConnection connection) {
        this.connection = connection;
        registerAllRoutes();
    }

    void forward(NetChannel session, int cmd, Chunk body) {
        connection.prepareClientSession(session);
        ClientSessionRef sessionRef = session.getSessionRef();
        routeTable.forward(connection.owner(), sessionRef, cmd, body);
    }

    private void registerAllRoutes() {
        for (String serviceClassName : ServiceName.values()) {
            registerServiceRoute(serviceClassName);
        }
    }

    private void registerServiceRoute(String serviceClassName) {
        String registryClassName = ServiceName.fullClassName(serviceClassName) + ROUTE_REGISTRY_SUFFIX;
        try {
            Class<?> registryClass = Class.forName(registryClassName);
            Method registerMethod = registryClass.getMethod(REGISTER_METHOD_NAME, ClientCmdRouteTable.class);
            registerMethod.invoke(null, routeTable);
        } catch (ClassNotFoundException ignored) {// 找不到就是没有需要处理的客户端协议;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("初始化客户端协议路由失败: serviceClass=" + serviceClassName,
                    unwrapReflectiveException(e));
        }
    }

    private static Throwable unwrapReflectiveException(ReflectiveOperationException e) {
        if (e instanceof InvocationTargetException invocationTargetException) {
            return invocationTargetException.getCause();
        }
        return e;
    }
}
