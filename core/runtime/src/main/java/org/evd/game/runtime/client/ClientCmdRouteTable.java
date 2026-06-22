package org.evd.game.runtime.client;

import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.DistributeConfig;
import org.evd.game.runtime.mailbox.MessageLocationSender;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientCmdRouteTable {
    private final Map<Integer, RouteEntry> routes = new LinkedHashMap<>();
    private MessageLocationSender locationSender = new MessageLocationSender();

    /**
     *
     * @param msgId 消息号id
     * @param serviceClassName LocationService,className简写
     * @param actorType atorType
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

    public void forward(Service sender, ClientSessionRef session, int msgId, byte[] body) {
        RouteEntry routeEntry = routes.get(msgId);
        if (routeEntry == null) {
            throw new IllegalStateException("未注册的客户端协议: msgId=" + msgId);
        }
        routeEntry.forward(locationSender, sender, session, msgId, body);
    }

    private record RouteEntry(String serviceClassName, ActorType actorType) {
        private void forward(MessageLocationSender locationSender, Service sender, ClientSessionRef session, int msgId, byte[] body) {
            // todo 进行转发

            switch (actorType) {
                case NONE -> {
                    CallPoint callPoint = DistributeConfig.getNodeByServiceClass(ServiceType.fullClassName(serviceClassName), 0);
                    if (callPoint == null) {
                        throw new IllegalStateException("找不到客户端协议目标服务: msgId=" + msgId
                                + ", service=" + serviceClassName);
                    }
                    sender.sendClientCmd(callPoint, session, msgId, new Chunk(body));
                }
                case PLAYER -> {
                    LocationInterface locationInterface = Service.getCurrent().getLocationInterface();
                    CallPoint callPoint = DistributeConfig.getNodeByServiceClass(ServiceType.fullClassName(ServiceType.LOC.getClassName()), 0);
                    ActorAddress actorAddress = locationInterface.get(callPoint, new ActorId(actorType, session.getPlayerId()));
                    //locationSender.send();
                }
                case MAP_PLAYER -> {
                }
                case MAP, GATE, GUILD -> throw new IllegalStateException("不能有 "+actorType +  " 类型: msgId=" + msgId
                        + ", service=" + serviceClassName);

            }
        }
    }
}
