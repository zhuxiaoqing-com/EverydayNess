package org.evd.game.ConnService;

import org.evd.game.runtime.ClientCmdRouteTable;
import org.evd.game.runtime.ClientSessionRef;
import org.evd.game.runtime.DistributeConfig;
import org.evd.game.runtime.netty.NetChannel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class ConnServiceClientCmdRouter {
    private static final String ROUTE_REGISTRY_SUFFIX = "ClientCmdRouteRegistry";
    private static final String REGISTER_METHOD_NAME = "register";

    private final ConnService owner;
    private final ClientCmdRouteTable routeTable = new ClientCmdRouteTable();

    ConnServiceClientCmdRouter(ConnService owner) {
        this.owner = owner;
        registerAllRoutes();
    }

    void forward(NetChannel session, int cmd, byte[] body) {
        ClientSessionRef sessionRef = owner.buildClientSessionRef(session);
        routeTable.forward(sessionRef, cmd, body);
    }

    private void registerAllRoutes() {
        for (String serviceClassName : DistributeConfig.getServiceClassNames()) {
            registerServiceRoute(serviceClassName);
        }
    }

    private void registerServiceRoute(String serviceClassName) {
        String registryClassName = toRegistryClassName(serviceClassName);
        try {
            Class<?> registryClass = Class.forName(registryClassName);
            Method registerMethod = registryClass.getMethod(REGISTER_METHOD_NAME, ClientCmdRouteTable.class);
            registerMethod.invoke(null, routeTable);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("初始化客户端协议路由失败: serviceClass=" + serviceClassName,
                    unwrapReflectiveException(e));
        }
    }

    private static String toRegistryClassName(String serviceClassName) {
        int lastDot = serviceClassName.lastIndexOf('.');
        String packageName = serviceClassName.substring(0, lastDot);
        String simpleClassName = serviceClassName.substring(lastDot + 1);
        return packageName + "." + simpleClassName + ROUTE_REGISTRY_SUFFIX;
    }

    private static Throwable unwrapReflectiveException(ReflectiveOperationException e) {
        if (e instanceof InvocationTargetException invocationTargetException) {
            return invocationTargetException.getCause();
        }
        return e;
    }
}
