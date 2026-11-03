package org.evd.game.runtime.Db.snapshot;

import io.protostuff.Schema;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.serialize.CachedProtostuffSerializer;
import org.evd.game.runtime.Db.table.TRecord;
import org.evd.game.runtime.Db.table.TTable;

import java.util.Objects;

/** Chronicle 快照值；实体在读取时直接恢复为对象。 */
public final class SnapshotValue implements BytesMarshallable {
    private Snapshot snapshot;
    private transient CachedProtostuffSerializer cachedSerializer;
    private transient Object entity;
    private transient Schema<?> entitySchema;
    private transient SnapshotStats snapshotStats;
    private transient String statsServiceId;


    private SnapshotValue(CachedProtostuffSerializer cachedSerializer) {
        this.cachedSerializer = Objects.requireNonNull(cachedSerializer, "cachedSerializer");
    }

    public SnapshotValue(TRecord<?, ?> record) {
        this(record, null, null);
    }

    SnapshotValue(TRecord<?, ?> record, String statsServiceId, SnapshotStats snapshotStats) {
        this(Objects.requireNonNull(record, "record").getSnapshotSerializer());
        TTable<?, ?> table = record.getTable();
        DirtyObject entity = record.getValue();
        this.snapshot = new Snapshot(record.getState(), entity.getMemVersion(), entity.getDbVersion());
        this.entity = entity;
        this.entitySchema = table.getSnapshotSchema();
        this.snapshotStats = snapshotStats;
        this.statsServiceId = snapshotStats == null ? null
                : Objects.requireNonNull(statsServiceId, "statsServiceId");
        validate();
    }

    /** 创建快照读取目标；实体由 readMarshallable 从字节流恢复，快照元数据也在读取时填充。 */
    public SnapshotValue(CachedProtostuffSerializer cachedSerializer,
                         Object entity, Schema<?> entitySchema) {
        this(cachedSerializer);
        this.entity = Objects.requireNonNull(entity, "entity");
        this.entitySchema = Objects.requireNonNull(entitySchema, "entitySchema");
    }

    public static SnapshotValue forMetadataRead(CachedProtostuffSerializer cachedSerializer) {
        return new SnapshotValue(cachedSerializer);
    }

    /** 创建仅供 ChronicleMap.averageValue 容量估算使用的快照样本，不代表实际记录。 */
    static <T> SnapshotValue forSizing(CachedProtostuffSerializer cachedSerializer,
                                       Snapshot snapshot, T entity, Schema<T> entitySchema) {
        SnapshotValue value = new SnapshotValue(cachedSerializer);
        value.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        value.entity = Objects.requireNonNull(entity, "entity");
        value.entitySchema = Objects.requireNonNull(entitySchema, "entitySchema");
        value.validate();
        return value;
    }

    public int recordState() {
        return snapshot.recordState();
    }

    /** 快照覆盖的内存版本，同时用于条件删除对应的 Chronicle 条目。 */
    public long snapshotVersion() {
        return snapshot.snapshotVersion();
    }

    public long dbVersion() {
        return snapshot.dbVersion();
    }

    public Object entity() {
        return entity;
    }

    public void validateMetadata() {
        if (snapshot == null) {
            throw new IllegalStateException("DB 快照缺少元数据");
        }
        snapshot.validate();
    }

    public void validate() {
        validateMetadata();
        if (cachedSerializer == null || entity == null || entitySchema == null) {
            throw new IllegalStateException("DB 快照缺少实体、Schema 或 Protostuff 序列化器");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeMarshallable(BytesOut<?> bytes) {
        validate();
        cachedSerializer.writeCachedProtostuff(bytes, snapshot, Snapshot.PROTOSTUFF_SCHEMA);
        int serializedLength = cachedSerializer.writeCachedProtostuff(bytes, entity, (Schema<Object>) entitySchema);
        if (snapshotStats != null) {
            snapshotStats.record(statsServiceId, entity.getClass(), serializedLength);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readMarshallable(BytesIn<?> bytes) {
        int metadataLength = bytes.readInt();
        if (metadataLength <= 0 || (long) metadataLength + Integer.BYTES > bytes.readRemaining()) {
            throw new IllegalStateException("DB 快照 Tag 元数据长度无效: length=" + metadataLength
                    + ", remaining=" + bytes.readRemaining());
        }
        snapshot = new Snapshot();
        cachedSerializer.readWithProtostuff(
                bytes, metadataLength, snapshot, Snapshot.PROTOSTUFF_SCHEMA);
        snapshot.validate();

        int entityLength = bytes.readInt();
        if (entityLength < 0 || entityLength > bytes.readRemaining()) {
            throw new IllegalStateException("DB 快照实体长度无效: length=" + entityLength
                    + ", remaining=" + bytes.readRemaining());
        }
        if (entitySchema == null) {
            entity = null;
            bytes.readSkip(entityLength);
            return;
        }

        cachedSerializer.readWithProtostuff(bytes, entityLength, entity, (Schema<Object>) entitySchema);
        validate();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SnapshotValue that)) {
            return false;
        }
        return cachedSerializer == that.cachedSerializer
                && Objects.equals(snapshot, that.snapshot)
                && entity == that.entity
                && entitySchema == that.entitySchema;
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshot, System.identityHashCode(entity),
                System.identityHashCode(entitySchema));
    }

}
