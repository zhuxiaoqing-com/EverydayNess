package org.evd.game.runtime.continuation;

/**
 * 协程锁类型统一收口，避免外部直接传裸 int 造出未约束的锁域。
 */
public enum LockType {
    ACTOR(1), // Actor 业务状态锁
    MAILBOX(2), // Actor mailbox 顺序锁
    LOCATION(3), // Location 状态锁
    TABLE_RECORD(4), // MDB 表内记录行锁
    DB_CACHE(5), // DB 缓存刷新锁
    LOCATION_CALL(6), // Location 调用锁
    LOGIN(7), // 登录流程锁
    TABLE(8), // MDB 表级锁

    ;

    private final int code;

    LockType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
