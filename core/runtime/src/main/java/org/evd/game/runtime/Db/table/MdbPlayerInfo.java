package org.evd.game.runtime.Db.table;

/** 一个玩家在当前 MDB 实例中的生命周期记录。 */
final class MdbPlayerInfo {
    private final long playerId;
    private MdbState state = MdbState.EMPTY;
    private boolean inUse;
    private boolean flushRequested;
    private long flushDeadline;

    MdbPlayerInfo(long playerId) {
        this.playerId = playerId;
    }

    long getPlayerId() {
        return playerId;
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

    boolean isFlushRequested() {
        return flushRequested;
    }

    void setFlushRequested(boolean flushRequested) {
        this.flushRequested = flushRequested;
    }

    long getFlushDeadline() {
        return flushDeadline;
    }

    void setFlushDeadline(long flushDeadline) {
        this.flushDeadline = flushDeadline;
    }

}
