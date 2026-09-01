package org.evd.game.PlayerService.session;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MapRoute;
import org.evd.game.runtime.call.CallPoint;

/** PlayerService 持有的玩家在线运行态。 */
public final class PPlayerOnline {
    /** 玩家地图状态；正式地图实例接入后仍由 PlayerService 持有这条状态。 */
    public enum MapState {
        NOT_ENTERED,
        ENTERING,
        ENTERED
    }

    public enum Status {
        LOADING_DATA,
        READY,
        ENTERING_MAP,
        ONLINE
    }

    private final String userId;
    private final long playerId;
    private final CallPoint gate;
    private final long gateSessionId;
    private final ActorAddress actorAddress;
    private ActorAddress gateActorAddress;
    private Status status;
    private MapState mapState = MapState.NOT_ENTERED;
    private MapRoute currentMap;
    private long mapEnterSeq;
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

    public MapState getMapState() {
        return mapState;
    }

    public MapRoute getCurrentMap() {
        return currentMap == null ? null
                : new MapRoute(currentMap.getMapConfigId(), currentMap.getMapInstanceId(), currentMap.getStage());
    }

    public long getMapEnterSeq() {
        return mapEnterSeq;
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

    /** 只绑定 GW 玩家地址，进入地图由 {@link #beginEnterMap()} 显式推进。 */
    boolean bindGateActorAddress(ActorAddress gateActorAddress) {
        if (gateActorAddress == null || status != Status.READY) {
            return false;
        }
        this.gateActorAddress = gateActorAddress;
        return true;
    }

    /** 发起进入地图；当前只推进状态，目标地图实例由后续流程接入。 */
    boolean beginEnterMap() {
        if (status != Status.READY || gateActorAddress == null || mapState != MapState.NOT_ENTERED) {
            return false;
        }
        mapEnterSeq++;
        mapState = MapState.ENTERING;
        transition(Status.ENTERING_MAP);
        return true;
    }

    /** 进入地图完成；只有进入中的玩家才能完成。 */
    boolean completeEnterMap(MapRoute route) {
        if (status != Status.ENTERING_MAP || mapState != MapState.ENTERING) {
            return false;
        }
        if (route == null || route.getStage() == null || route.getMapInstanceId() <= 0L) {
            return false;
        }
        currentMap = new MapRoute(route.getMapConfigId(), route.getMapInstanceId(), route.getStage());
        mapState = MapState.ENTERED;
        return true;
    }

    /** 完成进入地图，进入正式在线状态。 */
    boolean markOnline() {
        if (mapState != MapState.ENTERED || status != Status.ENTERING_MAP) {
            return false;
        }
        transition(Status.ONLINE);
        return true;
    }

    /** 记录当前玩家上线阶段；地图流程由 mapState 单独表达。 */
    private void transition(Status next) {
        status = next;
    }

}
