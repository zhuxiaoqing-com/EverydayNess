package org.evd.game.StageService;

import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.ConnServiceProxy;
import org.evd.game.common.proxy.LocationServiceProxy;
import org.evd.game.common.proto.C2S_Login;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_Login;
import org.evd.game.common.location.MessageLocationSender;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.Node;
import org.evd.game.annotation.Rpc;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorExecutionMode;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.config.DistributeConfig;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RuntimeUtils;

@Actor()
public class StageService extends Service {
    public int a;
    private final MessageLocationSender actorLocationSender = new MessageLocationSender(this::queryActorAddress);


    public StageService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Rpc
    public String doSome1(int a, Integer b) {
        String str = RuntimeUtils.createStr("{}::{}::doSome1()", node.getId(), id);
        System.out.println(str);
        LogCore.core.info(str);

        String result = ConnServiceProxy.inst().con(new CallPoint("node1", "conn1"));
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
    public void callHaHaHaActorRpc1(long actorId, int a, int b) {
        requireHaHaHaActor(actorId).rpc1(a, b);
    }

    @Rpc
    public void callHaHaHaActorRpc2(long actorId, Object a, Object b) {
        requireHaHaHaActor(actorId).rpc2(a, b);
    }

    @ClientCmd(MsgId.C2S_LOGIN_VALUE)
    public void login(ClientSessionRef session, C2S_Login req) {
        LogCore.core.info("StageService 收到客户端登录: service={}, sessionId={}, account={}", id, session.getSessionId(), req.getAccount());
        long actorId = session.getSessionId();
        bindActorLocation(actorId);

        S2C_Login resp = S2C_Login.newBuilder()
                .setRoleId(actorId)
                .setToken("token-" + req.getAccount())
                .build();
        ConnServiceProxy.inst().pushToClient(session.getGate(), session, MsgId.S2C_LOGIN_VALUE, new Chunk(resp));
    }

    public MessageLocationSender getActorLocationSender() {
        return actorLocationSender;
    }

    public MessageLocationSender getMessageLocationSender() {
        return actorLocationSender;
    }

    private void bindActorLocation(long actorId) {
        ActorId actorRef = ActorId.player(actorId);
        registerActor(actorRef, new HaHaHaActor(), ActorExecutionMode.ORDERED);
        ActorAddress actorAddress = getActorAddress(actorRef);
        CallPoint locationService = getLocationServiceRemote();
        LocationServiceProxy.inst().add(locationService, actorRef, actorAddress);
        actorLocationSender.cache(actorRef, actorAddress);
    }

    private HaHaHaActor requireHaHaHaActor(long actorId) {
        return requireActor(ActorId.player(actorId), HaHaHaActor.class);
    }

    private ActorAddress queryActorAddress(ActorId actorId) {
        return LocationServiceProxy.inst().get(getLocationServiceRemote(), actorId);
    }

    private CallPoint getLocationServiceRemote() {
        CallPoint remote = DistributeConfig.getNodeByServiceClass(
                "org.evd.game.LocationService.LocationService",
                0L);
        if (remote == null) {
            throw new IllegalStateException("找不到 LocationService 服务路由: org.evd.game.LocationService.LocationService");
        }
        return remote;
    }
}
