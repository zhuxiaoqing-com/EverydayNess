package org.evd.game.OnlineService.session;

/**
 * OnlineService 持有的玩家上线状态。
 *
 * <p>对象从开始上线就登记到 OnlinePlayerRegistry，直到对应网关会话下线才移除。
 * 因此它同时覆盖 PlayerService 上线 RPC 尚未返回的阶段，不需要再维护一个
 * 额外的临时上线对象。</p>
 */
public final class OnlinePlayer {
    public enum Status {
        LOADING_PLAYER,
        PLAYER_READY,
        GATE_BOUND,
        ONLINE
    }

    private final String userId;
    private final long playerId;
    private Status status = Status.LOADING_PLAYER;

    OnlinePlayer(String userId, long playerId) {
        this.userId = userId;
        this.playerId = playerId;
    }

    public String getUserId() {
        return userId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public Status getStatus() {
        return status;
    }

    /** Player Actor 绑定完成，玩家数据已准备好。 */
    void markPlayerReady() {
        transition(Status.PLAYER_READY);
    }

    /** GW Actor 绑定完成。 */
    void markGateBound() {
        transition(Status.GATE_BOUND);
    }

    /** PlayerService 确认进入地图完成后标记为 ONLINE。 */
    public void markOnline() {
        transition(Status.ONLINE);
    }

    /** 记录当前玩家上线阶段；状态仅用于观测，不参与流程判断。 */
    private void transition(Status next) {
        status = next;
    }

    @Override
    public String toString() {
        return "OnlinePlayer{" +
                "userId='" + userId + '\'' +
                ", playerId=" + playerId +
                ", status=" + status +
                '}';
    }
}
