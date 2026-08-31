package org.evd.game.runtime.Db.table;

/** 玩家 MDB 的生命周期状态。玩家在线状态不由此状态代替。 */
public enum MdbState {
    EMPTY(0),
    LOAD_WAIT(1),
    LOAD_ING(2),
    LOAD_FINISH(3),
    FLUSH_WAIT(4),
    FLUSH_ING(5),
    FLUSH_FINISH(6);

    private final int code;

    MdbState(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
