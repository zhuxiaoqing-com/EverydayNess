package org.evd.game.runtime.Db.table;

/** 一个玩家在当前 MDB 实例中的生命周期记录。 */
public final class MdbPlayerInfo {
    private final long playerId;
    private final String userId;
    private MdbState state = MdbState.EMPTY;
    private boolean inUse;
    private long flushDeadline;

    MdbPlayerInfo(long playerId, String userId) {
        this.playerId = playerId;
        this.userId = userId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public String getUserId() {
        return userId;
    }

    MdbState getState() {
        return state;
    }

    void setState(MdbState state) {
        this.state = state;
    }

    boolean isInUse() {
        return inUse;
    }

    void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    long getFlushDeadline() {
        return flushDeadline;
    }

    void setFlushDeadline(long flushDeadline) {
        this.flushDeadline = flushDeadline;
    }

}
