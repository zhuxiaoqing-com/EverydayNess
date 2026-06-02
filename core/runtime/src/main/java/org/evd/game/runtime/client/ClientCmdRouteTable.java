package org.evd.game.runtime.client;

import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.DistributeConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientCmdRouteTable {
    private final Map<Integer, RouteEntry> routes = new LinkedHashMap<>();

    public void register(int msgId, String serviceClassName) {
        RouteEntry routeEntry = new RouteEntry(serviceClassName);
        RouteEntry previous = routes.putIfAbsent(msgId, routeEntry);
        if (previous != null) {
            throw new IllegalStateException("客户端协议重复注册: msgId=" + msgId
                    + ", service=" + previous.serviceClassName
                    + ", service=" + serviceClassName);
        }
    }

    public void forward(Service sender, ClientSessionRef session, int msgId, byte[] body) {
        RouteEntry routeEntry = routes.get(msgId);
        if (routeEntry == null) {
            throw new IllegalStateException("未注册的客户端协议: msgId=" + msgId);
        }
        CallPoint callPoint = DistributeConfig.getNodeByServiceClass(routeEntry.serviceClassName, session.getRouteKey());
        if (callPoint == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: msgId=" + msgId
                    + ", service=" + routeEntry.serviceClassName);
        }
        routeEntry.forward(sender, callPoint, session, msgId, body);
    }

    private record RouteEntry(String serviceClassName) {
        private void forward(Service sender, CallPoint callPoint, ClientSessionRef session, int msgId, byte[] body) {
            sender.sendClientCmd(callPoint, null, session, msgId, new Chunk(body));
        }
    }
}
