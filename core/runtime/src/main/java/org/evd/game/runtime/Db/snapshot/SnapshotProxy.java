package org.evd.game.runtime.Db.snapshot;

import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.table.TRecord;
import org.evd.game.runtime.Db.table.TTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Chronicle 快照业务操作代理。 */
public final class SnapshotProxy {
    /** 记录快照业务校验和处理过程的日志记录器。 */
    private static final Logger logger = LoggerFactory.getLogger(SnapshotProxy.class);

    /** 创建快照业务代理。 */
    public SnapshotProxy() {
    }

    /**
     * 将记录当前状态写入 Chronicle，并在写入成功后更新记录的快照版本。
     *
     * @param manager 目标 Node 快照管理器
     * @param serviceId 当前 Service 标识
     * @param record 要捕获的记录
     * @param <K> 记录主键类型
     * @param <V> 数据库对象类型
     * @return 记录无需捕获或捕获成功时返回 {@code true}；捕获失败时返回 {@code false}
     */
    public <K, V extends DirtyObject> boolean captureSnapshot(
            SnapshotManager manager,
            String serviceId,
            TRecord<K, V> record) {
        if (!record.isModified() || !record.needsSnapshot()) {
            return true;
        }

        TTable<K, V> table = record.getTable();
        V value = record.getValue();
        int recordState = record.getState();
        long memoryVersion = value.getMemVersion();
        try {
            SnapshotKey key = manager.snapshotKey(table.getName(), record.getKey());
            SnapshotValue snapshotValue = new SnapshotValue(record, serviceId, manager.stats());
            manager.put(key, snapshotValue);

            value.setSnapshotVersion(memoryVersion);
            return true;
        } catch (RuntimeException e) {
            logger.error("DB 实体快照发布失败，记录仍保持 dirty: table={}, key={}, recordState={}, memVersion={}",
                    table.getName(), record.getKey(), recordState, memoryVersion, e);
            return false;
        }
    }

    /**
     * 当当前快照版本不高于 DB 已确认版本时，按 key 删除该记录的快照。
     *
     * @param manager 快照管理器
     * @param record 已推进 DB 版本的记录
     * @param <K> 记录主键类型
     * @param <V> 数据库对象类型
     */
    public <K, V extends DirtyObject> void removeSnapshotsCoveredByDbVersion(
            SnapshotManager manager,
            TRecord<K, V> record) {
        SnapshotKey key = manager.snapshotKey(record.getTable().getName(), record.getKey());
        V value = record.getValue();
        if (value.getSnapshotVersion() <= value.getDbVersion()) {
            manager.removeSnapshot(key);
        }
    }

    /**
     * 校验快照与目标表一致后，创建用于恢复该记录的数据库请求。
     *
     * @param table 目标数据库表
     * @param key 快照 key
     * @param snapshot 要重放的快照
     * @param <K> 记录主键类型
     * @param <V> 数据库对象类型
     * @return 保存或删除该记录的数据库请求
     * @throws DBException 快照、实体或主键与目标表不兼容时抛出
     */
    public <K, V extends DirtyObject> DBReq createReplayRequest(
            TTable<K, V> table, SnapshotKey key, SnapshotValue snapshot) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!Objects.equals(table.getName(), key.tableName())) {
            throw new DBException("快照数据库表与目标表不一致: snapshot=" + key.tableName()
                    + ", current=" + table.getName());
        }
        snapshot.validate();

        Object entity = snapshot.entity();
        @SuppressWarnings("unchecked")
        V value = (V) entity;
        if (value == null) {
            throw new DBException("DB 快照未恢复出实体对象: table=" + table.getName()
                    + ", key=" + key.key());
        }
        value.setMemVersion(snapshot.snapshotVersion());
        value.setDbVersion(snapshot.dbVersion());
        value.setSnapshotVersion(snapshot.snapshotVersion());
        @SuppressWarnings("unchecked")
        K primaryKey = (K) key.key();
        if (!Objects.equals(primaryKey, table.getSnapshotPrimaryKey(value))) {
            throw new DBException("快照主键与实体主键不一致: table=" + table.getName()
                    + ", key=" + primaryKey + ", entityKey=" + table.getSnapshotPrimaryKey(value));
        }
        return snapshot.recordState() == TRecord.REMOVE
                ? table.createRemoveDBReq(primaryKey)
                : table.createSaveDBReq(primaryKey, value);
    }

    /**
     * 校验快照的元数据和实体内容。
     *
     * @param snapshot 要校验的快照
     * @throws IllegalStateException 快照结构或内容无效时抛出
     */
    public void validateSnapshot(SnapshotValue snapshot) {
        snapshot.validate();
    }
}
