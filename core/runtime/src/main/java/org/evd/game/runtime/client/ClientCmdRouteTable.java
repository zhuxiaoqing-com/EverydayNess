package org.evd.game.runtime.client;

import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.annotation.ActorType;
import org.evd.game.runtime.call.CallPoint;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientCmdRouteTable {
    private final Map<Integer, RouteEntry> routes = new LinkedHashMap<>();

    /**
     *
     * @param msgId 消息号id
     * @param serviceClassName LocationService,className简写
     * @param actorType actorType
     */
    public void register(int msgId, String serviceClassName, ActorType actorType) {
        RouteEntry routeEntry = new RouteEntry(serviceClassName, actorType);
        RouteEntry previous = routes.putIfAbsent(msgId, routeEntry);
        if (previous != null) {
            throw new IllegalStateException("客户端协议重复注册: msgId=" + msgId
                    + ", serviceType=" + serviceClassName
                    + ", actorType=" + actorType);
        }
    }

    public void forward(Service sender, ClientSessionRef session, int msgId, Chunk body) {
        RouteEntry routeEntry = routes.get(msgId);
        if (routeEntry == null) {
            throw new IllegalStateException("未注册的客户端协议: msgId=" + msgId);
        }
        routeEntry.forward(sender, session, msgId, body);
    }

    private record RouteEntry(String serviceClassName, ActorType actorType) {
        private void forward(Service sender, ClientSessionRef session, int msgId, Chunk body) {

            switch (actorType) {
                case NONE -> {
                    CallPoint callPoint = sender.getNode().getAnyCallPointByType(ServiceType.byName(serviceClassName));
                    if(callPoint == null) {
                        throw new IllegalStateException("找不到客户端协议目标服务: msgId=" + msgId
                                + ", service=" + serviceClassName);
                    }
                    sender.sendClientCmd(callPoint, session, msgId, body);
                }
                case PLAYER,MAP_PLAYER ->
                        sender.getMessageLocationSender()
                                .sendClientCmd(new ActorId(actorType, session.getPlayerId()), session, msgId, body);
                case MAP, GATE -> throw new IllegalStateException("不能有 "+actorType +  " 类型: msgId=" + msgId
                        + ", service=" + serviceClassName);

            }
        }
    }
}
