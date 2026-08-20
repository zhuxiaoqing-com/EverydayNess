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
        STARTING,
        ONLINE
    }

    private final String userId;
    private final long playerId;
    private Status status = Status.STARTING;

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

    void markOnline() {
        status = Status.ONLINE;
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
