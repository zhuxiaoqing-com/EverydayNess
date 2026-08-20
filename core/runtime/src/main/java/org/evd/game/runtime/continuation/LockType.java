package org.evd.game.runtime.continuation;

/**
 * 协程锁类型统一收口，避免外部直接传裸 int 造出未约束的锁域。
 */
public enum LockType {
    ACTOR(1),
    MAILBOX(2),
    LOCATION(3),
    TABLE_RECORD(4),
    DB_CACHE(5),
    LOCATION_CALL(6), // location_call锁
    LOGIN(7),

    ;

    private final int code;

    LockType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
