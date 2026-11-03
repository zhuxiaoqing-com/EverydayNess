package org.evd.game.runtime.Db.snapshot;

import io.protostuff.Tag;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.evd.game.runtime.Db.table.TRecord;

import java.util.Objects;

/** 使用 Protostuff Tag 编码的数据库快照元数据。 */
public final class Snapshot {
    public static final Schema<Snapshot> PROTOSTUFF_SCHEMA = RuntimeSchema.getSchema(Snapshot.class);

    @Tag(1)
    private int recordState;
    @Tag(2)
    private long snapshotVersion;
    @Tag(3)
    private long dbVersion;

    public Snapshot() {
    }

    public Snapshot(int recordState, long snapshotVersion, long dbVersion) {
        this.recordState = recordState;
        this.snapshotVersion = snapshotVersion;
        this.dbVersion = dbVersion;
        validate();
    }

    public int recordState() {
        return recordState;
    }

    public long snapshotVersion() {
        return snapshotVersion;
    }

    public long dbVersion() {
        return dbVersion;
    }

    public void validate() {
        if (recordState != TRecord.ADD && recordState != TRecord.REMOVE
                && recordState != TRecord.GET) {
            throw new IllegalStateException("未知 DB 快照记录状态: " + recordState);
        }
        if (snapshotVersion <= 0L || dbVersion < 0L || dbVersion > snapshotVersion) {
            throw new IllegalStateException("DB 快照元数据无效: snapshotVersion=" + snapshotVersion
                    + ", dbVersion=" + dbVersion + ", recordState=" + recordState);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Snapshot that)) {
            return false;
        }
        return recordState == that.recordState
                && snapshotVersion == that.snapshotVersion
                && dbVersion == that.dbVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordState, snapshotVersion, dbVersion);
    }
}
