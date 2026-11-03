package org.evd.game.runtime.Db.snapshot;

import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

import java.util.Objects;

/** 唯一标识一个数据库表中的行。 */
public final class SnapshotKey implements BytesMarshallable {
    private String tableName;
    private Object key;

    public SnapshotKey() {
    }

    private SnapshotKey(String tableName, Object key) {
        this.tableName = Objects.requireNonNull(tableName, "tableName");
        this.key = Objects.requireNonNull(key, "key");
    }

    public static SnapshotKey of(String tableName, Object key) {
        Objects.requireNonNull(key, "key");
        if (key instanceof Integer || key instanceof Long || key instanceof String) {
            return new SnapshotKey(tableName, key);
        }
        throw new IllegalArgumentException("Chronicle 快照不支持的主键类型: " + key.getClass().getName());
    }

    public String tableName() {
        return tableName;
    }

    public Object key() {
        return key;
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeUtf8(tableName);
        if (key instanceof Integer value) {
            bytes.writeUtf8("I");
            bytes.writeUtf8(value.toString());
        } else if (key instanceof Long value) {
            bytes.writeUtf8("L");
            bytes.writeUtf8(value.toString());
        } else if (key instanceof String value) {
            bytes.writeUtf8("S");
            bytes.writeUtf8(value);
        } else {
            throw new IllegalStateException("快照主键类型损坏: " + key.getClass().getName());
        }
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        tableName = bytes.readUtf8();
        String keyValueType = bytes.readUtf8();
        String serializedKeyValue = bytes.readUtf8();
        key = switch (keyValueType) {
            case "I" -> Integer.valueOf(serializedKeyValue);
            case "L" -> Long.valueOf(serializedKeyValue);
            case "S" -> serializedKeyValue;
            default -> throw new IllegalStateException("快照主键类型损坏: " + keyValueType);
        };
        Objects.requireNonNull(tableName, "快照 tableName");
        Objects.requireNonNull(key, "快照 key");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SnapshotKey that)) {
            return false;
        }
        return Objects.equals(tableName, that.tableName) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, key);
    }

    @Override
    public String toString() {
        return "SnapshotKey{" + tableName + ", " + key + '}';
    }
}
