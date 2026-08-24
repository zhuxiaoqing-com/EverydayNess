package org.evd.game.PlayerService.session;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.call.CallPoint;

/** PlayerService 持有的玩家在线运行态。 */
public final class PPlayerOnline {
    public enum Status {
        LOADING_DATA,
        READY,
        ENTERING_MAP,
        ONLINE
    }

    private final String userId;
    private final CallPoint gate;
    private final long gateSessionId;
    private final ActorAddress actorAddress;
    private ActorAddress gateActorAddress;
    private Status status;

    public PPlayerOnline(String userId, CallPoint gate, long gateSessionId,
                         ActorAddress actorAddress, Status status) {
        this.userId = userId;
        this.gate = gate;
        this.gateSessionId = gateSessionId;
        this.actorAddress = actorAddress;
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public CallPoint getGate() {
        return gate;
    }

    public long getGateSessionId() {
        return gateSessionId;
    }

    public ActorAddress getActorAddress() {
        return actorAddress;
    }

    public ActorAddress getGateActorAddress() {
        return gateActorAddress;
    }

    public Status getStatus() {
        return status;
    }

    /** 玩家数据加载完成，进入可上线状态。 */
    void markReady() {
        transition(Status.READY);
    }

    /** 绑定 GW 玩家地址并进入地图流程。 */
    void bindGateActorAddress(ActorAddress gateActorAddress) {
        transition(Status.ENTERING_MAP);
        this.gateActorAddress = gateActorAddress;
    }

    /** 完成进入地图，进入正式在线状态。 */
    void markOnline() {
        transition(Status.ONLINE);
    }

    /** 记录当前玩家上线阶段；状态仅用于观测，不参与流程判断。 */
    private void transition(Status next) {
        status = next;
    }
}
