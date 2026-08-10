package org.evd.game.runtime.Db.table;

import org.evd.game.annotation.ServiceType;
import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.table.util.TimeCostPrint;
import org.evd.game.runtime.util.RuntimeUtils;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.RegisteredService;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;

public class Mdb {
    public static Logger logger = LoggerFactory.getLogger(Mdb.class.getName());

    private enum LifecycleState {
        NEW,
        RUNNING,
        CLOSING,
        CLOSED
    }

    public Mdb() {
    }

    private final Map<Class<?>, TTable<?, ?>> class2TableMap = new HashMap<>();
    private final List<TTable<?, ?>> tableList = new ArrayList<>();


    DBExecInterface dbExecInterface;
    Service service;

    private volatile LifecycleState lifecycleState = LifecycleState.NEW;


    public void start(Class<?> ownerClass, DBExecInterface dbExecInterface, Service service) {
        if (lifecycleState != LifecycleState.NEW) {
            throw new DBException("MDB不能重复调用start: state=" + lifecycleState);
        }
        logger.warn("@@@@@@@@@@@@@@@@ mdb start begin @@@@@@@@@@@@@@@@ mdb metadata {}", ownerClass.getName());
        if(dbExecInterface == null){
            throw  new DBException("DBExecInterface is null !!!");
        }
        this.dbExecInterface = dbExecInterface;
        this.service = service;

        try {
            TableRegistry tableRegistry = loadTableRegistry(ownerClass);
            tableRegistry.register(this);
            lifecycleState = LifecycleState.RUNNING;
            logger.warn("{} has {} tables ......", ownerClass.getSimpleName(), tableList.size());
            service.launchCoroutine(() -> {
                if (!checkTableCreate()) {
                    logger.warn("MDB等待DBService连接: service={}", service.getId());
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
        String registryClassName = ownerClass.getPackageName() + ".db.DbTableRegistry";
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

    public void registerTable(Class<?> apiClass, Class<? extends TTable<?, ?>> implClass) {
        try {
            TTable<?, ?> tTable = createTableInstance(implClass);
            registerTableAlias(apiClass, tTable);
            if (apiClass != implClass) {
                registerTableAlias(implClass, tTable);
            }
            tTable.setMdb(this);
            tableList.add(tTable);
        } catch (Exception e) {
            throw new DBException("registerTable fail apiClass=" + apiClass + " implClass=" + implClass, e);
        }
    }

    private void registerTableAlias(Class<?> tableClass, TTable<?, ?> tTable) {
        tTable.setMdb(this);
        TTable<?, ?> old = class2TableMap.putIfAbsent(tableClass, tTable);
        if (old != null) {
            logger.error("tableClass 重复！！！！ {} ", tableClass);
            throw new DBException("tableClass 重复！！！！ " + tableClass);
        }
    }


    /**
     * 直接玩家线程加载,然后抛到db线程get,然后再回调回来;不管失败;当然如果报错了 就算失败不让进;
     * 不 这个的直接加载,不然就要吧get方法拆开了;这里要么就直接加载吧;get拆开其cache就有问题;
     * 感觉不要这个方法算了;
     */
    @SuppressWarnings("unchecked")
    public void loadPlayerAllTableToMemory(Object key) {
        logger.info("loadPlayerAllTableToMemory key {}", key);
        TimeCostPrint timeCostPrint = new TimeCostPrint(logger, 60, "loadPlayerAllTableToMemory: " + key);
        try {
            for (TTable table : tableList) {
                if (!table.isSupportFlush()) {
                    continue;
                }
                table.get(key);
            }
            timeCostPrint.print();
        } catch (Exception e) {
            logger.error("Mdb loadPlayerAllTableToMemory error key {} ", key, e);
            throw new DBException(e);
        }
    }

    /*
     * flush 失败以后 需要业务层找机会再次调用;
     * 这里的flush按理来说应该是要一步的

     * 业务层必须有重试功能;
     */

    /**
     * 直接本地进行序列化，然后抛到db线程进行存储，然后再抛回来进行删除数据;
     * 如果在这个期间玩家重新登录的话，那是不是就不应该删除呢?所以来一个function判断是否删除该数据就行了;这个先不弄吧;
     * 这里flush失败就失败了，clearCache里会保底，这里的clearCache里也会清理可以flush的数据，如果不能清理就麻烦了，比如flush失败以后预约下次flush;
     * 当然也可以业务层再次预约，看实现;底层只需要保证完善就行;
     *
     * todo flush这里其实还没弄好;flush成功以后需要同步给location服务器，将其退出，否则就还在Location服务器，不变;
     */

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void flush(Object key) {
        logger.info("flush flushLogicThread start key {}", key);

        TimeCostPrint timeCostPrint = new TimeCostPrint(logger, 60, "flush: " + key);

        List<TRecord> jsonList = new ArrayList<>();

        for (TTable table : tableList) {
            if (!table.isSupportFlush()) {
                continue;
            }

            TRecord tRecord = table.getTRecordByCache(key);
            if (tRecord == null) {
                continue;
            }

            // 没有修改过
            if (!tRecord.isModified()) {
                table.deleteCache(key);
                continue;
            }
            // 修改过了
            jsonList.add(tRecord);
        }


        if (jsonList.isEmpty()) {
            return;
        }

        logger.info("flush flushMdbSaveDB start key {} jsonListSize {} ", key, jsonList.size());

        boolean allSuccess = true;
        ArrayList<TRecord> successTRecord = new ArrayList<>();
        for (TRecord entry : jsonList) {

            boolean flush = entry.flush("flush");
            if (!flush) {
                allSuccess = false;
                continue;
            }
            successTRecord.add(entry);
        }

        logger.info("flush flushMdbSaveDB summary key {} jsonListSize {} successTRecordSize {} ", key, jsonList.size(), successTRecord.size());

        boolean success = allSuccess;
        // 将保存到mysql成功的进行删除cache;
        for (TRecord record : successTRecord) {
            TRecord currTRecord = record.getTable().getTRecordByCache(record.getKey());
            // cache里的不是当前的record了;
            if (currTRecord != record) {
                success = false;
                continue;
            }
            // 修改过了
            if (record.isModified()) {
                success = false;
                continue;
            }

            record.getTable().deleteCache(record.getKey());
        }

         /*   if (success) {
                // 这里再检查一遍;
                for (TTable table : tableList) {
                    TTable tt = table;
                    if (!tt.isSupportFlush()) {
                        continue;
                    }

                    TRecord t = tt.getTRecordByCache(key);
                    if (t == null) {
                        continue;
                    }
                    // 还有没删除的！！！
                    success = false;
                    break;
                }

            }*/

        timeCostPrint.print();
    }

    public static final int TICK_INTERVAL = 1000 * 10;
    public long nextTickTime;
    @SuppressWarnings({"rawtypes"})
    public void tick(long currTime) {
        if (lifecycleState != LifecycleState.RUNNING || allCallPoint().isEmpty()) {
            return;
        }
        if (currTime < nextTickTime) {
            return;
        }
        nextTickTime = currTime + TICK_INTERVAL;

       Service.getCurrent().launchCoroutine(()-> tickCoroutine(Service.getTime()));
    }

    public void tickCoroutine(long currTime) {
        if (lifecycleState != LifecycleState.RUNNING || allCallPoint().isEmpty()) {
            return;
        }
        for (TTable table : tableList) {
            table.tick(currTime);
        }

        for (TTable table : tableList) {
            table.checkpoint(false);
        }

        for (TTable table : tableList) {
            // 支持flush 说明是玩家个人数据; 不清理让玩家手动调用flush清理
            if (!table.isSupportFlush()) {
                table.tickClearCache();
            }
        }
    }

    public boolean checkTableCreate() {
        CallPoint callPoint = service.getNode().getAnyCallPointByType(ServiceType.DB);
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

        for (TTable tables : tableList) {
            tables.close(runCheckpoint);
        }
        tableList.clear();
        class2TableMap.clear();

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
        TTable<?, ?> table = class2TableMap.get(clazz);
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


    public void disconnectService(Collection<RegisteredService> collection) {
        if (lifecycleState != LifecycleState.RUNNING) {
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
        return service.getNode().getCallPointByType(ServiceType.DB);
    }

}
