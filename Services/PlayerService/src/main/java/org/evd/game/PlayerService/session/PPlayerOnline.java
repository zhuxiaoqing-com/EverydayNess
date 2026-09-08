package org.evd.game.PlayerService.session;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.LogCore;

/** PlayerService 持有的玩家在线运行态。 */
public final class PPlayerOnline {
    public enum Status {
        LOADING_DATA,
        READY,
        ONLINE
    }

    private final String userId;
    private final long playerId;
    private final CallPoint gate;
    private final long gateSessionId;
    private final ActorAddress actorAddress;
    private ActorAddress gateActorAddress;
    private Status status;
    /** 对账异常计数属于当前玩家绑定；新 Session 会创建新的 PPlayerOnline。 */
    private int onlineMissingCount;

    public PPlayerOnline(String userId, long playerId, CallPoint gate, long gateSessionId,
                         ActorAddress actorAddress, Status status) {
        this.userId = userId;
        this.playerId = playerId;
        this.gate = gate;
        this.gateSessionId = gateSessionId;
        this.actorAddress = actorAddress;
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public long getPlayerId() {
        return playerId;
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

    /** 记录当前玩家与 Online 的连续对账异常；返回值表示已连续发现两轮。 */
    public boolean observeOnlineReconcileMismatch() {
        return ++onlineMissingCount >= 2;
    }

    public void clearOnlineReconcileMismatch() {
        onlineMissingCount = 0;
    }

    /** 玩家数据加载完成，进入可上线状态。 */
    void markReady() {
        transition(Status.READY);
    }

    /** 只绑定 GW 玩家地址，地图转场由 PlayerMapLogic 推进。 */
    boolean bindGateActorAddress(ActorAddress gateActorAddress) {
        if (gateActorAddress == null || status != Status.READY) {
            return false;
        }
        this.gateActorAddress = gateActorAddress;
        return true;
    }

    /** 登录流程准备完成后，进入正式在线状态。 */
    void markOnline() {
        if (status != Status.READY) {
            LogCore.core.warn("PlayerService 玩家标记在线失败: userId={}, playerId={}, status={}",
                    userId, playerId, status);
            return;
        }
        transition(Status.ONLINE);
    }

    /** 记录当前玩家上线阶段。 */
    private void transition(Status next) {
        status = next;
    }

}
