package org.evd.game.runtime.Db.table;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.table.util.TimeCostPrint;
import org.evd.game.runtime.util.RuntimeUtils;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Consumer;

public class Mdb {
    public static Logger logger = LoggerFactory.getLogger(Mdb.class.getName());
    private static final String TABLE_REGISTRY_SUFFIX = ".db.DbTableRegistry";

    private enum LifecycleState {
        NEW,
        RUNNING,
        CLOSING,
        CLOSED
    }

    public Mdb() {
        // TimerState已经在构造函数中初始化了，不需要再次初始化
    }

    /**
     * 判断 Service 是否生成了自己的数据库表注册器。
     *
     * <p>数据库表由 dbDef APT 生成到 Service 包下的 db.DbTableRegistry，
     * 因此注册器是否存在就是该 Service 是否需要 Mdb 的编译产物依据。</p>
     */
    public static boolean hasTableRegistry(Class<?> ownerClass) {
        try {
            Class.forName(tableRegistryClassName(ownerClass), false, ownerClass.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String tableRegistryClassName(Class<?> ownerClass) {
        return ownerClass.getPackageName() + TABLE_REGISTRY_SUFFIX;
    }

    private final Map<Class<?>, TTable<?, ?>> tableClass2TableMap = new HashMap<>();
    private final Map<Class<? extends DirtyObject>, TTable<?, ?>> beanClass2TableMap = new HashMap<>();
    private final List<TTable<?, ?>> tableList = new ArrayList<>();

    DBExecInterface dbExecInterface;
    Service service;

    private volatile LifecycleState lifecycleState = LifecycleState.NEW;
    private MdbPlayerManager mdbPlayerManager;


    public void start(Class<?> ownerClass, DBExecInterface dbExecInterface, Service service) {
        if (lifecycleState != LifecycleState.NEW) {
            throw new DBException("MDB不能重复调用start: state=" + lifecycleState);
        }
        logger.warn("@@@@@@@@@@@@@@@@ mdb start begin @@@@@@@@@@@@@@@@ mdb metadata {}", ownerClass.getName());
        if (dbExecInterface == null) {
            throw new DBException("DBExecInterface is null !!!");
        }
        this.dbExecInterface = dbExecInterface;
        this.service = service;
        this.mdbPlayerManager = new MdbPlayerManager(this, service);

        try {
            TableRegistry tableRegistry = loadTableRegistry(ownerClass);
            tableRegistry.register(this);
            lifecycleState = LifecycleState.RUNNING;
            logger.warn("{} has {} tables ......", ownerClass.getSimpleName(), tableList.size());
            service.launchCoroutine(() -> {
                if (!checkTableCreate()) {
                    logger.warn("MDB等待数据库连接: service={}", service.getId());
                    return;
                }
                logger.warn("@@@@@@@@@@@@@@@@  mdb remote start end  @@@@@@@@@@@@@@@@");
            });
            logger.warn("@@@@@@@@@@@@@@@@  mdb local start end  @@@@@@@@@@@@@@@@");
        } catch (Throwable e) {
            logger.error("Mdb start error", e);
            closeInternal(true, false);
            throw new RuntimeException(e);
        }

    }

    private TableRegistry loadTableRegistry(Class<?> ownerClass) throws Exception {
        String registryClassName = tableRegistryClassName(ownerClass);
        Class<?> registryClass;
        try {
            registryClass = Class.forName(registryClassName);
        } catch (ClassNotFoundException e) {
            throw new DBException("未找到 DbTableRegistry: " + registryClassName, e);
        }

        if (!TableRegistry.class.isAssignableFrom(registryClass)) {
            throw new DBException("DbTableRegistry 未实现 TableRegistry: " + registryClassName);
        }

        Constructor<?> constructor = registryClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (TableRegistry) constructor.newInstance();
    }

    private TTable<?, ?> createTableInstance(Class<? extends TTable<?, ?>> tableClass) throws Exception {
        Constructor<?> tableConstructor = tableClass.getDeclaredConstructor();
        tableConstructor.setAccessible(true);
        return (TTable<?, ?>) tableConstructor.newInstance();
    }



    public void registerTable(Class<?> tableFacadeClass,
                              Class<? extends DirtyObject> beanClass,
                              Class<? extends TTable<?, ?>> tableImplClass) {
        try {
            TTable<?, ?> tTable = createTableInstance(tableImplClass);
            tTable.setMdb(this);
            registerTableAlias(tableFacadeClass, tTable);
            if (tableImplClass != tableFacadeClass) {
                registerTableAlias(tableImplClass, tTable);
            }
            if (beanClass != null) {
                registerBeanClassAlias(beanClass, tTable);
            }
            tableList.add(tTable);
        } catch (Exception e) {
            throw new DBException("registerTable fail tableFacadeClass=" + tableFacadeClass
                    + " tableImplClass=" + tableImplClass, e);
        }
    }

    private void registerTableAlias(Class<?> tableClass, TTable<?, ?> tTable) {
        TTable<?, ?> old = tableClass2TableMap.putIfAbsent(tableClass, tTable);
        if (old != null) {
            logger.error("tableClass 重复！！！！ {} ", tableClass);
            throw new DBException("tableClass 重复！！！！ " + tableClass);
        }
    }

    private void registerBeanClassAlias(Class<? extends DirtyObject> beanClass, TTable<?, ?> tTable) {
        TTable<?, ?> old = beanClass2TableMap.putIfAbsent(beanClass, tTable);
        if (old != null) {
            throw new DBException("DB Bean class 重复: " + beanClass.getName());
        }
    }


    /** 请求加载玩家 MDB；已加载或尚未开始 flush 时直接复用现有 cache。 */
    public void loadPlayerAllTableToMemory(long playerId, String userId) {
        mdbPlayerManager.load(playerId, userId);
    }

    /** 设置玩家 MDB 缓存真正清理后的回调。 */
    public void setPlayerCacheExpiredCallback(Consumer<MdbPlayerInfo> callback) {
        mdbPlayerManager.setPlayerCacheExpiredCallback(callback);
    }

    /** 返回当前 MDB 中仍保留的玩家 userId，用于 PlayerService 上线时向 OnlineService 恢复历史绑定。 */
    public List<String> getPlayerUserIds() {
        return mdbPlayerManager.getPlayerUserIds();
    }

    /** 在 MdbPlayerManager 已持有玩家生命周期锁时执行实际表加载。 */
    void loadPlayerAllTableToMemoryInternal(long playerId) {
        Object key = playerId;
        logger.info("loadPlayerAllTableToMemory key {}", key);
        TimeCostPrint timeCostPrint = new TimeCostPrint(logger, 60, "loadPlayerAllTableToMemory: " + key);
        for (TTable<?, ?> table : tableList) {
            if (!table.isSupportFlush()) {
                continue;
            }
            try {
                table.getForLoad(playerId);
            } catch (RuntimeException e) {
                logger.error("Mdb loadPlayerAllTableToMemory error key {} table {}", key, table.getName(), e);
                throw e;
            }
        }
        timeCostPrint.print();
    }

    /**
     * 按表顺序提交玩家当前的 dirty 数据，返回是否所有表都成功。
     * 失败不会回滚 MDB 内存状态；本方法不重试，也不保证跨表事务原子性。
     */
    @SuppressWarnings({"rawtypes"})
    boolean flushPlayerAllTableToMemory(long playerId) {
        logger.info("flush flushLogicThread start key {}", playerId);

        TimeCostPrint timeCostPrint = new TimeCostPrint(logger, 60, "flush: " + playerId);
        boolean allSuccess = true;
        int tableCount = 0;
        for (TTable table : tableList) {
            if (!table.isSupportFlush()) {
                continue;
            }
            tableCount++;
            if (!table.flushPlayer(playerId)) {
                allSuccess = false;
            }
        }
        logger.info("flush flushMdbSaveDB summary key {} tableCount {} success {} ", playerId, tableCount, allSuccess);
        timeCostPrint.print();
        return allSuccess;
    }

    /**
     * 同步单个已进入 MDB 缓存的 DB 对象。
     *
     * <p>先通过表操作修改内存，再调用此方法落库。例如删除一条已缓存记录：</p>
     * <pre>{@code
     * Mdb mdb = Service.getCurrent().getMdb();
     * DBRoleMapData data = DBRoleMapDataTable.get(playerId);
     * if (data != null && DBRoleMapDataTable.remove(playerId)) {
     *     mdb.asyncSyncNow(data);
     * }
     * }</pre>
     */
    public boolean asyncSyncNow(DirtyObject object) {
        if (object == null) {
            throw new DBException("立即同步 DB 对象不能为空");
        }
        return asyncSyncNow(Set.of(object));
    }

    /**
     * 同步多个已进入 MDB 缓存的 DB 对象；所有对象在一个数据库事务中提交。
     *
     * <p>新增、修改或删除先更新各自的内存表，再把对应对象放入集合：</p>
     * <pre>{@code
     * Mdb mdb = Service.getCurrent().getMdb();
     * DBPlayerData player = DBPlayerDataTable.get(playerId);
     * player.setName(newName);                       // 修改已有缓存对象
     *
     * DBRoleMapData map = new DBRoleMapData();
     * map.setPlayerId(playerId);
     * DBRoleMapDataTable.add(playerId, map);          // 新增到 MDB 内存
     *
     * mdb.asyncSyncNow(Set.of(player, map));          // 两张表同一事务落库
     * }</pre>
     *
     * <p>对象必须来自当前 MDB 缓存；跨 DBService 的对象不能放进同一个事务。</p>
     */
    public boolean asyncSyncNow(Set<? extends DirtyObject> objects) {
        ensureImmediateSyncAvailable();
        if (objects == null) {
            throw new DBException("立即同步 DB 对象列表不能为空");
        }

        List<DBReq> transactionRequests = new ArrayList<>(objects.size());
        Map<TRecord<?, ?>, Long> snapshotVersions = new LinkedHashMap<>();

        for (DirtyObject object : objects) {
            if (object == null) {
                throw new DBException("立即同步 DB 对象不能为空");
            }
            TTable<?, ?> table = getTableForBeanClass(object.getClass());
            TRecord<?, ?> record = table.getCache(object);
            if (!record.isModified()) {
                continue;
            }

            CallPoint ownerCallPoint = record.getOwnerCallPoint();
            if (ownerCallPoint == null) {
                throw new DBException("立即同步记录没有数据库归属: table=" + table.getName()
                        + ", key=" + record.getKey());
            }
            snapshotVersions.put(record, record.getValue().getMemVersion());
            transactionRequests.add(record.createSyncRequest());
        }
        return executeImmediateSync(transactionRequests, snapshotVersions);
    }

    private void ensureImmediateSyncAvailable() {
        if (lifecycleState != LifecycleState.RUNNING) {
            throw new DBException("MDB 当前不可立即同步: state=" + lifecycleState);
        }
    }

    private TTable<?, ?> getTableForBeanClass(Class<?> beanClass) {
        TTable<?, ?> table = beanClass2TableMap.get(beanClass);
        if (table == null) {
            throw new DBException("MDB DB Bean 未注册: " + beanClass.getName());
        }
        return table;
    }

    private boolean executeImmediateSync(List<DBReq> transactionRequests,
                                         Map<TRecord<?, ?>, Long> snapshotVersions) {
        if (transactionRequests.isEmpty()) {
            return true;
        }

        Iterator<TRecord<?, ?>> records = snapshotVersions.keySet().iterator();
        CallPoint callPoint = records.next().getOwnerCallPoint();
        while (records.hasNext()) {
            CallPoint recordCallPoint = records.next().getOwnerCallPoint();
            if (!Objects.equals(callPoint, recordCallPoint)) {
                throw new DBException("MDB 立即事务记录路由到多个 DBService，无法使用同一事务: callPoints="
                        + callPoint + ", " + recordCallPoint
                        + ", tables=" + snapshotVersions.keySet().stream()
                                .map(record -> record.getTable().getName())
                                .distinct()
                                .toList());
            }
        }

        DBReq transaction = new DBReq();
        transaction.setDbOpType(DbOpType.TRANSACTION);
        transaction.setTransactionRequests(transactionRequests);
        DBRsp response;
        try {
            response = dbExecInterface.dbExec(callPoint, transaction);
        } catch (RuntimeException e) {
            logger.error("MDB 立即事务调用失败: tables={}",
                    snapshotVersions.keySet().stream()
                            .map(record -> record.getTable().getName())
                            .distinct()
                            .toList(),
                    e);
            return false;
        }
        if (response == null || !response.isSuccess()) {
            logger.error("MDB 立即事务失败: tables={}, error={}",
                    snapshotVersions.keySet().stream()
                            .map(record -> record.getTable().getName())
                            .distinct()
                            .toList(),
                    response == null ? "DBRsp 为空" : response.getExceptionMessage());
            return false;
        }

        snapshotVersions.forEach(TRecord::checkpointRefreshState);
        return true;
    }

    @SuppressWarnings("unchecked")
    void clearPlayerCache(long playerId) {
        for (TTable table : tableList) {
            if (table.isSupportFlush()) {
                table.deletePlayerCache(playerId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    boolean clearPlayerCacheIfClean(long playerId) {
        for (TTable table : tableList) {
            if (!table.isSupportFlush()) {
                continue;
            }
            if (table.hasModifiedPlayerCache(playerId)) {
                logger.warn("玩家 MDB flush 后清理 cache 前发现数据再次修改: playerId={}, table={}",
                        playerId, table.getName());
                return false;
            }
        }
        clearPlayerCache(playerId);
        return true;
    }

    public void playerLogout(long playerId) {
        mdbPlayerManager.logout(playerId);
    }

    void checkPlayerAccess(Object key, boolean forLoad) {
        if (!isSupportFlush()) {
            return;
        }
        mdbPlayerManager.checkAccess(toPlayerId(key), forLoad);
    }

    ContinuationLockScope awaitPlayerLifecycleLock(long playerId) {
        return service.awaitCoroutineLockScope(LockType.MDB_PLAYER, playerId);
    }

    private long toPlayerId(Object key) {
        if (key instanceof Number number) {
            return number.longValue();
        }
        throw new DBException("玩家 MDB key 不是合法 playerId: " + key);
    }

    // 统一的tick间隔，所有TTable共用
    public static final int TICK_INTERVAL = 1000 * 60; // 1分钟
    public static final int CHECKPOINT_INTERVAL = 1000 * 60; // 1分钟
    public static final int TICK_CLEAR_CACHE_INTERVAL = 1000 * 60; // 1分钟

    // 使用TimerState来管理定时任务状态
    private final TimerState tickTimerState = new TimerState(TICK_INTERVAL);
    private final TimerState checkpointTimerState = new TimerState(CHECKPOINT_INTERVAL);
    private final TimerState tickClearCacheTimerState = new TimerState(TICK_CLEAR_CACHE_INTERVAL);

    @SuppressWarnings({"rawtypes"})
    public void tick(long currTime) {
        if (lifecycleState != LifecycleState.RUNNING || allCallPoint().isEmpty()) {
            return;
        }

        mdbPlayerManager.tick(currTime);

        // 检查并执行tick任务
        if (tickTimerState.canExecute(currTime)) {
            tickTimerState.markStart();
            Service.getCurrent().launchCoroutine(() -> {
                try {
                    tickCoroutine(Service.getTime());
                } finally {
                    tickTimerState.markComplete(); // 协程结束后才设置下一次执行时间
                }
            });
        }

        // 检查并执行checkpoint任务
        if (checkpointTimerState.canExecute(currTime)) {
            checkpointTimerState.markStart();
            Service.getCurrent().launchCoroutine(() -> {
                try {
                    checkpointCoroutine(Service.getTime());
                } finally {
                    checkpointTimerState.markComplete(); // 协程结束后才设置下一次执行时间
                }
            });
        }

        // 检查并执行tickClearCache任务
        if (tickClearCacheTimerState.canExecute(currTime)) {
            tickClearCacheTimerState.markStart();
            Service.getCurrent().launchCoroutine(() -> {
                try {
                    tickClearCacheCoroutine(Service.getTime());
                } finally {
                    tickClearCacheTimerState.markComplete(); // 协程结束后才设置下一次执行时间
                }
            });
        }
    }

    public void tickCoroutine(long currTime) {
        if (lifecycleState != LifecycleState.RUNNING || allCallPoint().isEmpty()) {
            return;
        }

        // 执行tick操作（1分钟一次）
        for (TTable table : tableList) {
            table.tick(currTime);
        }
    }

    /**
     * 执行checkpoint操作（1分钟一次）
     */
    public void checkpointCoroutine(long currTime) {
        if (lifecycleState != LifecycleState.RUNNING || allCallPoint().isEmpty()) {
            return;
        }

        logger.info("Starting checkpoint execution at {}", currTime);

        for (TTable table : tableList) {
            table.checkpoint(false);
        }

        logger.info("Completed checkpoint execution");
    }

    /**
     * 执行tickClearCache操作（1分钟一次）
     */
    public void tickClearCacheCoroutine(long currTime) {
        if (lifecycleState != LifecycleState.RUNNING || allCallPoint().isEmpty()) {
            return;
        }

        logger.info("Starting tickClearCache execution at {}", currTime);

        // 执行tickClearCache操作（1分钟一次）
        for (TTable table : tableList) {
            // 支持flush 说明是玩家个人数据; 不清理让玩家手动调用flush清理
            if (!table.isSupportFlush()) {
                table.tickClearCache();
            }
        }

        logger.info("Completed tickClearCache execution");
    }

    public boolean checkTableCreate() {
        List<CallPoint> callPoints = allCallPoint();
        CallPoint callPoint = callPoints.isEmpty() ? null : callPoints.getFirst();
        if (callPoint == null) {
            logger.warn("checkCreateTable callPoint == null");
            return false;
        }
        for (TTable<?, ?> tTable : tableList) {
            tTable.checkCreateTable(callPoint);
        }
        logger.info("checkTableCreate success!!!");
        return true;
    }

    public void close() {
        closeInternal(false, true);
    }


    @SuppressWarnings({"rawtypes"})
    private void closeInternal(boolean force, boolean runCheckpoint) {
        if (!force && lifecycleState != LifecycleState.RUNNING) {
            return;
        }
        lifecycleState = LifecycleState.CLOSING;
        long currTime = System.currentTimeMillis();
        logger.info("@@@@@@@@@@@@@@@@  mdb stop begin   @@@@@@@@@@@@@@@@  {}", service.getId());

        if (mdbPlayerManager != null) {
            mdbPlayerManager.close();
        }
        for (TTable tables : tableList) {
            tables.close(runCheckpoint);
        }
        tableList.clear();
        tableClass2TableMap.clear();
        beanClass2TableMap.clear();

        lifecycleState = LifecycleState.CLOSED;
        long endTime = System.currentTimeMillis();
        logger.info("@@@@@@@@@@@@@@@@  mdb stop end   @@@@@@@@@@@@@@@@  {}  costMill {}",service.getId(), endTime - currTime);
    }



    @SuppressWarnings("unchecked")
    public <K, V extends DirtyObject> TTable<K, V> getTTable(Class<?> clazz) {
        if (lifecycleState != LifecycleState.RUNNING) {
            String serviceId = service == null ? "unknown" : service.getId();
            throw new DBException("MDB未就绪: service=" + serviceId + ", state=" + lifecycleState);
        }
        if (clazz == null) {
            throw new DBException("MDB表类型不能为空");
        }
        TTable<?, ?> table = tableClass2TableMap.get(clazz);
        if (table == null) {
            throw new DBException("MDB表未注册: " + clazz.getName());
        }
        return (TTable<K, V>) table;
    }


    public boolean isClosing() {
        return lifecycleState == LifecycleState.CLOSING;
    }

    public boolean isSupportFlush() {
        return service.getServiceType().isSupportFlush();
    }

    public DBExecInterface getDbExecInterface() {
        return dbExecInterface;
    }

    public boolean isLocal() {
        return service.getNode().hasNodeDbExecutor();
    }

    public void disconnectService(Collection<RegisteredService> collection) {
        if (lifecycleState != LifecycleState.RUNNING) {
            return;
        }
        if(isLocal()) {
            return;
        }
        Set<CallPoint> disconnCallPointSet = collection.stream()
                .filter(a -> a.getServiceType() == ServiceType.DB)
                .map(RegisteredService::getCallPoint).collect(Collectors.toSet());

        if(disconnCallPointSet.isEmpty()) {
            return;
        }

        // 先清除已经断开的归属，再通过延迟切换重新分配。
        for (TTable<?, ?> tTable : tableList) {
            for (TRecord<?, ?> tRecord : tTable.getCacheList()) {
                if (disconnCallPointSet.contains(tRecord.getOwnerCallPoint())) {
                    tRecord.clearCallPoint();
                }
            }
        }

        reassignUnownedRecords();
    }

    public void connectService(Collection<RegisteredService> collection) {
        if (lifecycleState != LifecycleState.RUNNING) {
            return;
        }
        if(isLocal()) {
            return;
        }
        boolean hasDBService = collection.stream()
                .anyMatch(a -> a.getServiceType() == ServiceType.DB);

        if (!hasDBService) {
            return;
        }

        if (!checkTableCreate()) {
            return;
        }
        reassignUnownedRecords();
    }

    private void reassignUnownedRecords() {
        List<CallPoint> callPoints = allCallPoint();
        if (callPoints.isEmpty()) {
            return;
        }

        for (TTable<?, ?> tTable : tableList) {
            for (TRecord<?, ?> tRecord : tTable.getCacheList()) {
                if (tRecord.getOwnerCallPoint() == null) {
                    tRecord.setWillCallPoint(RuntimeUtils.mod(tRecord.getKey(), callPoints));
                }
            }
        }
    }


    public CallPoint findDBServiceCallPoint(Object key) {
        List<CallPoint> callPoints = allCallPoint();
        return RuntimeUtils.mod(key, callPoints);
    }

    /**
     * 获取所有callPoint
     */
    public List<CallPoint> allCallPoint() {
        if (service.getNode().hasNodeDbExecutor()) {
            return List.of(service.getNode().getNodeDbCallPoint());
        }
        return service.getCallPointByType(ServiceType.DB);
    }

}
