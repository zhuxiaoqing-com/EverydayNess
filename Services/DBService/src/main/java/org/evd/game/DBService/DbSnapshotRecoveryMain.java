package org.evd.game.DBService;

import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.ProtostuffSerializer;
import org.evd.game.runtime.Db.snapshot.SnapshotKey;
import org.evd.game.runtime.Db.snapshot.SnapshotProxy;
import org.evd.game.runtime.Db.snapshot.SnapshotValue;
import org.evd.game.runtime.Db.snapshot.SnapshotManager;
import org.evd.game.runtime.Db.table.TRecord;
import org.evd.game.runtime.Db.table.DbTableRegistrar;
import org.evd.game.runtime.Db.table.TableRegistry;
import org.evd.game.runtime.Db.table.TTable;
import org.evd.game.base.DirtyObject;
import org.evd.game.base.DBException;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.ymlconfig.DbTopology;
import org.evd.game.runtime.ymlconfig.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** 启动前恢复本地 Chronicle Map 中未确认写入 MySQL 的实体快照。 */
public final class DbSnapshotRecoveryMain {
    private static final Logger logger = LoggerFactory.getLogger(DbSnapshotRecoveryMain.class);
    private static final SnapshotProxy SNAPSHOT_PROXY = new SnapshotProxy();

    private DbSnapshotRecoveryMain() {
    }

    public static void main(String[] args) throws Exception {
        String bootstrapName = args.length > 0 ? args[0] : "Bootstrap-all.yml";
        int nodeId = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        System.exit(run(bootstrapName, nodeId) ? 2 : 0);
    }

    public static boolean run(String bootstrapName, int nodeId)
            throws IOException, ClassNotFoundException {
        GlobalYml.init(bootstrapName, nodeId);
        NodeInfo localNode = GlobalYml.requireLocalNodeInfo();
        var nodeConfig = GlobalYml.requireNodeConfig();
        int platformId = nodeConfig.getPlatformId();
        int serverId = nodeConfig.getServerId();
        Path snapshotFile = SnapshotManager.fileForNode(platformId, serverId, localNode.getNodeId());
        try {
            Files.readAttributes(snapshotFile, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            logger.info("本地 Chronicle Map 文件不存在，无需恢复: nodeId={}, file={}", nodeId, snapshotFile);
            return false;
        }

        SnapshotManager snapshotManager = SnapshotManager.openExistingForRecovery(
                platformId, serverId, localNode.getNodeId());
        if (snapshotManager == null) {
            throw new IOException("DB 快照文件在检查后消失: " + snapshotFile);
        }

        if (nodeConfig.getDbTopology() != DbTopology.NODE_LOCAL) {
            throw new UnsupportedOperationException(
                    "独立恢复目前通过本 Node 的 DBYml 直连 MySQL；REMOTE_SERVICE 路由不能安全映射到本地连接");
        }

        try {
            if (snapshotManager.size() > 0L) {
                try (ProtostuffSerializer snapshotSerializer = new ProtostuffSerializer();
                     DBProxy dbProxy = new DBProxy()) {
                    dbProxy.initForRecovery();
                    RecoveryTableRegistrar registrar = new RecoveryTableRegistrar(snapshotSerializer);
                    TableRegistry.registerAllFor(registrar);
                    Map<String, TTable<?, ?>> recoveryTables = registrar.tablesByName;
                    registerTableMetadata(snapshotManager.keys(), recoveryTables, dbProxy);
                    recoverSnapshots(snapshotManager, recoveryTables, dbProxy, snapshotSerializer);
                }
            }

            if (snapshotManager.size() > 0L) {
                throw new IllegalStateException("DB 快照恢复后仍有未清理记录，快照文件已保留: nodeId=" + nodeId
                        + ", pending=" + snapshotManager.size());
            }
        } catch (ClassNotFoundException | RuntimeException | Error e) {
            try {
                snapshotManager.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }

        snapshotManager.closeAndDeleteIfEmpty();
        logger.warn("DB 快照恢复完成；本次进程必须退出，重启后才会启动游戏服务: nodeId={}", nodeId);
        return true;
    }

    private static TTable<?, ?> instantiateRecoveryTable(
            Class<? extends TTable<?, ?>> tableClass,
            ProtostuffSerializer serializer) {
        try {
            Constructor<?> constructor = tableClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            TTable<?, ?> table = (TTable<?, ?>) constructor.newInstance();
            table.initializeForRecovery(serializer);
            return table;
        } catch (ReflectiveOperationException e) {
            throw new DBException("实例化快照恢复 TTable 失败: " + tableClass.getName(), e);
        }
    }

    private static final class RecoveryTableRegistrar implements DbTableRegistrar {
        private final Map<String, TTable<?, ?>> tablesByName = new HashMap<>();
        private final Map<String, String> dbClassesByTableName = new HashMap<>();
        private final ProtostuffSerializer serializer;

        private RecoveryTableRegistrar(ProtostuffSerializer serializer) {
            this.serializer = serializer;
        }

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
            if (tablesByName.containsKey(checkedTableName)) {
                return;
            }
            TTable<?, ?> table = instantiateRecoveryTable(tableImplClass, serializer);
            if (!checkedTableName.equals(table.getName())) {
                throw new IllegalStateException("APT DB 表名与 TTable 不匹配: registered="
                        + checkedTableName + ", actual=" + table.getName()
                        + ", table=" + tableImplClass.getName());
            }
            tablesByName.put(checkedTableName, table);
        }
    }

    private static void registerTableMetadata(Set<SnapshotKey> keys,
                                              Map<String, TTable<?, ?>> recoveryTables,
                                              DBProxy dbProxy) {
        for (SnapshotKey key : keys) {
            TTable<?, ?> table = recoveryTables.get(key.tableName());
            if (table == null) {
                throw new IllegalStateException("快照包含当前 Node 未注册的数据库表: " + key.tableName());
            }
            dbProxy.registerRecoveryTable(table.createCreateTableDBReq());
        }
    }

    private static void recoverSnapshots(SnapshotManager snapshotManager,
                                         Map<String, TTable<?, ?>> recoveryTables,
                                         DBProxy dbProxy,
                                         ProtostuffSerializer snapshotSerializer) {
        for (SnapshotKey key : snapshotManager.keys()) {
            TTable<?, ?> table = recoveryTables.get(key.tableName());
            if (table == null) {
                throw new IllegalStateException("找不到快照表的当前注册器: " + key.tableName());
            }

            SnapshotValue snapshot = snapshotManager.get(key,
                    new SnapshotValue(snapshotSerializer,
                            table.newSnapshotValue(), table.getSnapshotSchema()));
            if (snapshot == null) {
                continue;
            }
            SNAPSHOT_PROXY.validateSnapshot(snapshot);

            DBRsp currentResponse = dbProxy.dbExecRecovery(createGetRequest(table, key.key()));
            requireSuccessfulResponse(currentResponse, "读取 MySQL 当前行失败", key, snapshot);

            DirtyObject databaseValue = (DirtyObject) table.parseGetDBRsp(currentResponse);
            if (snapshot.recordState() == TRecord.REMOVE && databaseValue == null) {
                snapshotManager.removeSnapshot(key);
                continue;
            }

            if (databaseValue != null && databaseValue.getDbVersion() > snapshot.snapshotVersion()) {
                snapshotManager.removeSnapshot(key);
                logger.info("MySQL 行版本高于 Chronicle 快照，保留数据库行并清理旧快照: tableName={}, key={}, dbVersion={}, snapshotVersion={}",
                        key.tableName(), key, databaseValue.getDbVersion(), snapshot.snapshotVersion());
                continue;
            }

            DBRsp response = dbProxy.dbExecRecovery(
                    SNAPSHOT_PROXY.createReplayRequest(table, key, snapshot));
            requireSuccessfulResponse(response, "DB 快照保存请求失败", key, snapshot);
            snapshotManager.removeSnapshot(key);
            logger.info("恢复 DB 快照成功: tableName={}, key={}, recordState={}, version={}",
                    key.tableName(), key, snapshot.recordState(), snapshot.snapshotVersion());
        }
    }

    private static void requireSuccessfulResponse(DBRsp response, String operation,
                                                  SnapshotKey key, SnapshotValue snapshot) {
        if (response == null || !response.isSuccess()) {
            String error = response == null ? "DBRsp 为空" : response.getExceptionMessage();
            throw new DBException(operation + ": tableName=" + key.tableName() + ", key=" + key
                    + ", version=" + snapshot.snapshotVersion() + ", error=" + error);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DBReq createGetRequest(TTable<?, ?> table, Object key) {
        return ((TTable) table).createGetDBReq(key);
    }

}
