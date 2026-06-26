package org.evd.game.ConnService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proto.C2S_ConnPing;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.S2C_ConnPing;
import org.evd.game.common.proxy.StageService.StageServiceProxy;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.actor.ActorType;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RuntimeUtils;

@Actor()
public class ConnService extends Service {
    boolean first = true;
    private final ConnServiceClientCmdRouter clientCmdRouter;
    private final ConnServiceClientTransport clientTransport;

    public ConnService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.clientCmdRouter = new ConnServiceClientCmdRouter(this);
        this.clientTransport = new ConnServiceClientTransport(this, serviceInfo.getPublicAddr());
        setPublicAddr(serviceInfo.getPublicAddr());
    }

    @Override
    public void init() {
        LogCore.core.info("ConnService Init");
        clientTransport.start();
    }

    @Override
    public void tick() {
        if (!first) {
            return;
        }
        first = false;
        launchCoroutine(this::requestStageDemo);
    }

    private void requestStageDemo() {
        CallPoint callPoint = new CallPoint("node2", "stage1");
        String s = StageServiceProxy.inst().doSome1(callPoint, 1, 2);
        System.out.println("receive = " + s);
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
        String s = StageServiceProxy.inst().doSome3(new CallPoint("", ""), 1);
    }

    public void dispatchClientCmd(NetChannel session, int cmd, byte[] body) {
        clientCmdRouter.forward(session, cmd, body);
    }

    @Rpc(actorType = ActorType.GATE)
    public void pushToClient(ClientSessionRef session, int msgId, Chunk body) {
        ActorId actorId = gateActorId(session.getSessionId());
        if (!hasActor(actorId)) {
            throw RpcCallException.actorNotFound(actorId);
        }
        clientTransport.pushToClient(session, msgId, body);
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

    ClientSessionRef buildClientSessionRef(NetChannel session) {
        return clientTransport.buildClientSessionRef(session);
    }

    public void setPublicAddr(String publicAddr) {
        clientTransport.setPublicAddr(publicAddr);
    }

    public void setClientBossThreads(int clientBossThreads) {
        clientTransport.setClientBossThreads(clientBossThreads);
    }

    public void setClientWorkerThreads(int clientWorkerThreads) {
        clientTransport.setClientWorkerThreads(clientWorkerThreads);
    }

    public void setClientMaxFrameLength(int clientMaxFrameLength) {
        clientTransport.setClientMaxFrameLength(clientMaxFrameLength);
    }

    @Override
    public void onClose() {
        clientTransport.shutdown();
        super.onClose();
    }

    void registerClientSessionActor(NetChannel session) {
        ActorId actorId = gateActorId(session.getChannelId());
        if (!hasActor(actorId)) {
            registerActor(actorId, MailBoxType.UNORDERED);
        }
    }

    void unregisterClientSessionActor(long sessionId) {
        unregisterActor(gateActorId(sessionId));
    }

    private ActorId gateActorId(long sessionId) {
        return ActorId.gate(sessionId);
    }

    @Override
    protected boolean supportMdb() {
        return false;
    }
}
