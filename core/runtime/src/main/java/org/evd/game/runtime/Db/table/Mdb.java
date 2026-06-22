package org.evd.game.runtime.Db.table;

import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.table.util.TimeCostPrint;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mdb {
    public static Logger logger = LoggerFactory.getLogger(Mdb.class.getName());

    public Mdb() {
    }

    private final Map<Class<?>, TTable<?, ?>> class2TableMap = new HashMap<>();
    private final List<TTable<?, ?>> tableList = new ArrayList<>();


    DBExecInterface dbExecInterface;

    public boolean running = false;
    private volatile boolean closing = false;


    public synchronized void start(String name, DBExecInterface dbExecInterface) throws Exception {
        logger.warn("@@@@@@@@@@@@@@@@ mdb start begin @@@@@@@@@@@@@@@@ mdb metadata {}", name);
        closing = false;
        if(dbExecInterface == null){
            throw  new DBException("DBExecInterface is null !!!");
        }
        this.dbExecInterface = dbExecInterface;

        try {
            //todo 换成指定的类
            Class<?> tableClass = Class.forName("table." + name + "._Tables_");
            Constructor<?> constructor = tableClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object tableOwner = constructor.newInstance();
            Class<?>[] classes = tableClass.getDeclaredClasses();
            for (Class<?> aClass : classes) {
                if (!TTable.class.isAssignableFrom(aClass)) {
                    continue;
                }
                TTable<?, ?> tTable = createTableInstance(tableOwner, tableClass, aClass);
                tTable.setMdb(this);
                tableList.add(tTable);
                TTable<?, ?> old = class2TableMap.putIfAbsent(aClass, tTable);
                if (old != null) {
                    logger.error("aClass 重复！！！！ {} ", aClass);
                    throw new DBException("aClass 重复！！！！ " + aClass);
                }
            }
            logger.warn("{} has {} tables ......", name, tableList.size());

            logger.warn("@@@@@@@@@@@@@@@@  mdb start end  @@@@@@@@@@@@@@@@");
            running = true;
        } catch (Throwable e) {
            logger.error("Mdb start error", e);
            closeInternal(true, false);
            throw e;
        }

    }

    private TTable<?, ?> createTableInstance(Object tableOwner, Class<?> tableClass, Class<?> nestedTableClass) throws Exception {
        Constructor<?> tableConstructor;
        if (Modifier.isStatic(nestedTableClass.getModifiers())) {
            tableConstructor = nestedTableClass.getDeclaredConstructor();
            tableConstructor.setAccessible(true);
            return (TTable<?, ?>) tableConstructor.newInstance();
        }
        tableConstructor = nestedTableClass.getDeclaredConstructor(tableClass);
        tableConstructor.setAccessible(true);
        return (TTable<?, ?>) tableConstructor.newInstance(tableOwner);
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

    @SuppressWarnings({"rawtypes"})
    public void tick() {
        long currTime = System.currentTimeMillis();
        for (TTable table : tableList) {
            table.tick(currTime);
        }

        for (TTable table : tableList) {
            table.checkpoint();
        }

        for (TTable table : tableList) {
            // 支持flush 说明是玩家个人数据; 不清理让玩家手动调用flush清理
            if (!table.isSupportFlush()) {
                table.tickClearCache();
            }
        }
    }


    public synchronized void close() {
        closeInternal(false, true);
    }


    @SuppressWarnings({"rawtypes"})
    private void closeInternal(boolean force, boolean runCheckpoint) {
        if (!force && !running) {
            return;
        }
        closing = true;
        logger.info("@@@@@@@@@@@@@@@@  mdb stop begin   @@@@@@@@@@@@@@@@");

        for (TTable tables : tableList) {
            tables.close();
        }
        tableList.clear();
        class2TableMap.clear();

        running = false;
        logger.info("@@@@@@@@@@@@@@@@  mdb stop end   @@@@@@@@@@@@@@@@");
    }



    @SuppressWarnings("unchecked")
    public <K, V extends DirtyObject> TTable<K, V> getTTable(Class<? extends TTable<K, V>> clazz) {
        return (TTable<K, V>) class2TableMap.get(clazz);
    }


    public boolean isClosing() {
        return closing;
    }

    public DBExecInterface getDbExecInterface() {
        return dbExecInterface;
    }


}
