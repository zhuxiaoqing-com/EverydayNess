package org.evd.game.runtime.Db.snapshot;

/** 当前数据库行的可合并快照操作。 */
public enum SnapshotOperation {
    UPSERT(1),
    DELETE(2);

    private final int code;

    SnapshotOperation(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static SnapshotOperation fromCode(int code) {
        return switch (code) {
            case 1 -> UPSERT;
            case 2 -> DELETE;
            default -> throw new IllegalStateException("未知 DB 快照操作码: " + code);
        };
    }
}
