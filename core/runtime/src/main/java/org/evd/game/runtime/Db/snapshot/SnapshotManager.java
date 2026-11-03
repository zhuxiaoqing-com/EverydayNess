package org.evd.game.runtime.Db.snapshot;

import io.protostuff.Schema;
import io.protostuff.Tag;
import io.protostuff.runtime.RuntimeSchema;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.serialize.ProtostuffSerializer;
import org.evd.game.runtime.Db.table.DbTableRegistrar;
import org.evd.game.runtime.Db.table.TRecord;
import org.evd.game.runtime.Db.table.TTable;
import org.evd.game.runtime.Db.table.TableRegistry;
import org.evd.game.runtime.util.RuntimeUtils;
import org.evd.game.runtime.ymlconfig.DbSnapshotStoreYml;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 一个 Node 持有一个持久化快照 Map，并集中提供访问和统计。 */
public final class SnapshotManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotManager.class);
    private static final SnapshotProxy SNAPSHOT_PROXY = new SnapshotProxy();
    private static final Schema<SnapshotSizeSample> SAMPLE_SCHEMA =
            RuntimeSchema.getSchema(SnapshotSizeSample.class);

    private final Path file;
    private final FileChannel lockChannel;
    private final FileLock fileLock;
    private final ChronicleMap<SnapshotKey, SnapshotValue> map;
    private final SnapshotStats stats = new SnapshotStats();
    private final long configuredEntries;

    private SnapshotManager(Path file, FileChannel lockChannel, FileLock fileLock,
                            ChronicleMap<SnapshotKey, SnapshotValue> map,
                            long configuredEntries) {
        this.file = file;
        this.lockChannel = lockChannel;
        this.fileLock = fileLock;
        this.map = map;
        this.configuredEntries = configuredEntries;
    }

    public static Path fileForNode(int platformId, int serverId, int nodeId) {
        return directoryPath().resolve("node-" + platformId + "-" + serverId + "-" + nodeId + ".map");
    }

    public static SnapshotManager open(int platformId, int serverId, int nodeId)
            throws IOException, ClassNotFoundException {
        return openWithRegisteredTables(fileForNode(platformId, serverId, nodeId),
                platformId + "-" + serverId + "-" + nodeId,
                false);
    }

    /** 供同包测试显式指定容量；生产入口从快照配置读取。 */
    static SnapshotManager open(int platformId, int serverId, int nodeId,
                                long entries, long averageValueSizeBytes,
                                Set<String> expectedTableNames)
            throws IOException {
        return openWithCapacity(fileForNode(platformId, serverId, nodeId),
                platformId + "-" + serverId + "-" + nodeId,
                entries, averageValueSizeBytes, expectedTableNames, false);
    }

    public static SnapshotManager openExistingForRecovery(int platformId, int serverId, int nodeId)
            throws IOException, ClassNotFoundException {
        Path file = fileForNode(platformId, serverId, nodeId);
        if (!Files.exists(file)) {
            return null;
        }
        return openWithRegisteredTables(file, platformId + "-" + serverId + "-" + nodeId,
                true);
    }

    private static SnapshotManager openWithRegisteredTables(Path file, String nodeId, boolean recover)
            throws IOException, ClassNotFoundException {
        SnapshotTableRegistrar registrar = new SnapshotTableRegistrar();
        TableRegistry.registerAllFor(registrar);
        DbSnapshotStoreYml config = GlobalYml.requireDbConfig().getDb().getSnapshotStore();
        return openWithCapacity(file, nodeId, config.getEntries(), config.getAverageValueSizeBytes(),
                registrar.requireTableNames(), recover);
    }

    private static SnapshotManager openWithCapacity(
            Path file, String nodeId, long entries, long averageValueSizeBytes,
            Set<String> expectedTableNames, boolean recover)
            throws IOException {
        Objects.requireNonNull(expectedTableNames, "expectedTableNames");
        if (entries <= 0L) {
            throw new IllegalArgumentException("Chronicle Map entries 必须大于 0: " + entries);
        }
        if (averageValueSizeBytes <= 0L || averageValueSizeBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Chronicle Map averageValueSizeBytes 必须在 1 到 "
                    + Integer.MAX_VALUE + " 之间: " + averageValueSizeBytes);
        }
        Files.createDirectories(file.getParent());
        Path lockPath = file.resolveSibling(file.getFileName() + ".lock");
        FileChannel lockChannel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock fileLock;
        try {
            fileLock = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            closeQuietly(lockChannel);
            throw new IOException("Node DB 快照文件已在本 JVM 中被占用: " + file, e);
        } catch (IOException e) {
            closeQuietly(lockChannel);
            throw e;
        }
        if (fileLock == null) {
            closeQuietly(lockChannel);
            throw new IOException("Node DB 快照文件被其他进程占用: " + file);
        }

        ChronicleMap<SnapshotKey, SnapshotValue> map = null;
        try {
            try (ProtostuffSerializer sampleSerializer = new ProtostuffSerializer()) {
                SnapshotValue sampleValue = SnapshotValue.forSizing(sampleSerializer,
                        new Snapshot(TRecord.ADD, 1L, 0L),
                        new SnapshotSizeSample(new byte[(int) averageValueSizeBytes]), SAMPLE_SCHEMA);
                ChronicleMapBuilder<SnapshotKey, SnapshotValue> builder = ChronicleMap
                        .of(SnapshotKey.class, SnapshotValue.class)
                        .name("evd-db-snapshot-node-" + nodeId)
                        .averageKey(SnapshotKey.of("snapshot_capacity_sample", 123456789L))
                        .averageValue(sampleValue)
                        .putReturnsNull(true)
                        .removeReturnsNull(true)
                        .entries(entries);
                if (recover) {
                    // 恢复旧文件时由文件头提供原始容量配置，允许当前 YAML 容量已变化。
                    map = builder.recoverPersistedTo(file.toFile(), false);
                } else {
                    map = builder.createPersistedTo(file.toFile());
                }
            }
            Set<String> unknownTables = map.keySet().stream()
                    .map(SnapshotKey::tableName)
                    .filter(tableName -> !expectedTableNames.contains(tableName))
                    .collect(java.util.stream.Collectors.toSet());
            if (!unknownTables.isEmpty()) {
                throw new IOException("Node 快照包含当前 Node 未注册的 DB 表: " + unknownTables);
            }
            long mapFileSizeBytes = Files.size(file);
            long estimatedValueBytes = Math.multiplyExact(entries, averageValueSizeBytes);
            SnapshotManager manager = new SnapshotManager(file, lockChannel, fileLock, map, entries);
            map = null;
            logger.info("Node DB 快照 Map 已打开: node={}, file={}, recover={}, entries={}, "
                            + "averageValueSize={}KB, estimatedValueMemory={}M, mapFileSize={}M, pending={}",
                    nodeId, file, recover, entries, averageValueSizeBytes / 1024.0d,
                    RuntimeUtils.formatMegabytes(estimatedValueBytes),
                    RuntimeUtils.formatMegabytes(mapFileSizeBytes), manager.map.size());
            return manager;
        } catch (Throwable e) {
            if (map != null) {
                try {
                    map.close();
                } catch (RuntimeException closeException) {
                    e.addSuppressed(closeException);
                }
            }
            try {
                fileLock.release();
            } finally {
                closeQuietly(lockChannel);
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("打开 Node DB 快照 Map 失败: " + file, e);
        }
    }

    SnapshotKey snapshotKey(String tableName, Object key) {
        return SnapshotKey.of(tableName, key);
    }

    public SnapshotValue get(SnapshotKey key, SnapshotValue usingValue) {
        return map.getUsing(key, usingValue);
    }

    public void put(SnapshotKey key, SnapshotValue value) {
        map.put(key, value);
    }

    public void removeSnapshot(SnapshotKey key) {
        map.remove(key);
    }

    public java.util.Set<SnapshotKey> keys() {
        return new HashSet<>(map.keySet());
    }

    public <K, V extends DirtyObject> boolean captureSnapshot(String serviceId, TRecord<K, V> record) {
        return SNAPSHOT_PROXY.captureSnapshot(this, serviceId, record);
    }

    public <K, V extends DirtyObject> void removeSnapshotsCoveredByDbVersion(
            TRecord<K, V> record) {
        SNAPSHOT_PROXY.removeSnapshotsCoveredByDbVersion(this, record);
    }

    public SnapshotStats stats() {
        return stats;
    }

    public Path file() {
        return file;
    }

    public long size() {
        return map.size();
    }

    public void logStats(int nodeId) {
        stats.logAndReset(nodeId, file, map.size(), configuredEntries);
    }

    private static Path directoryPath() {
        String directory = System.getProperty("evd.db.snapshot.dir");
        if (directory == null || directory.isBlank()) {
            directory = GlobalYml.requireDbConfig().getDb().getSnapshotStore().getDirectory();
        }
        return Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void close() throws IOException {
        close(false);
    }

    /** 关闭 Map；为空时在独占锁仍持有期间删除 .map 文件，保留 .lock 文件。 */
    public void closeAndDeleteIfEmpty() throws IOException {
        close(true);
    }

    private void close(boolean deleteIfEmpty) throws IOException {
        IOException failure = null;
        boolean mapIsEmpty = false;
        if (deleteIfEmpty) {
            try {
                long pending = map.size();
                mapIsEmpty = pending == 0L;
                if (!mapIsEmpty) {
                    logger.warn("Node DB 快照 Map 仍有待处理记录，保留文件: file={}, pending={}",
                            file, pending);
                }
            } catch (RuntimeException e) {
                failure = new IOException("检查 Node DB 快照 Map 是否为空失败: " + file, e);
            }
        }
        try {
            map.close();
        } catch (RuntimeException e) {
            IOException closeFailure = new IOException("关闭 Node DB 快照 Map 失败: " + file, e);
            if (failure == null) {
                failure = closeFailure;
            } else {
                failure.addSuppressed(closeFailure);
            }
        }
        if (mapIsEmpty && failure == null) {
            try {
                Files.deleteIfExists(file);
                logger.info("Node DB 快照 Map 已清空，删除文件以按当前配置重建: file={}", file);
            } catch (IOException e) {
                failure = new IOException("删除空 Node DB 快照 Map 失败: " + file, e);
            }
        }
        try {
            fileLock.release();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            lockChannel.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // 保留 Map 打开或文件锁失败这一主因。
        }
    }

    private static final class SnapshotTableRegistrar implements DbTableRegistrar {
        private final Set<String> tableNames = new LinkedHashSet<>();
        private final Map<String, String> dbClassesByTableName = new HashMap<>();

        @Override
        public void registerTable(Class<?> tableFacadeClass,
                                  Class<? extends DirtyObject> beanClass,
                                  Class<? extends TTable<?, ?>> tableImplClass,
                                  String tableName,
                                  Object keySample) {
            String checkedTableName = Objects.requireNonNull(tableName, "tableName");
            String dbClassName = Objects.requireNonNull(beanClass, "beanClass").getName();
            String previousDbClass = dbClassesByTableName.putIfAbsent(checkedTableName, dbClassName);
            if (previousDbClass != null && !previousDbClass.equals(dbClassName)) {
                throw new IllegalStateException("同一 Node 的数据库表名映射到多个 DB 类: tableName="
                        + checkedTableName + ", first=" + previousDbClass + ", next=" + dbClassName);
            }
            tableNames.add(checkedTableName);
            // 校验 APT 提供的每张表主键类型；Chronicle 容量估算使用内部固定样本。
            SnapshotKey.of(checkedTableName, keySample);
        }

        private Set<String> requireTableNames() {
            if (tableNames.isEmpty()) {
                throw new IllegalStateException("ServiceType 中没有可持久化 DB 表");
            }
            return Set.copyOf(tableNames);
        }

    }

    private static final class SnapshotSizeSample {
        @Tag(1)
        private byte[] payload;

        public SnapshotSizeSample() {
        }

        private SnapshotSizeSample(byte[] payload) {
            this.payload = payload;
        }
    }
}
