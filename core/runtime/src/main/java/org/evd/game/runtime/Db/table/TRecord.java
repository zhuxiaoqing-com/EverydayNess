package org.evd.game.runtime.Db.table;

import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.util.TimeUtils;
import org.evd.game.runtime.call.CallPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class TRecord<K, V extends DirtyObject> {
    public static final int ADD = 1;
    public static final int REMOVE = 2;
    public static final int GET = 3;
    private static final Logger logger = LoggerFactory.getLogger(TRecord.class);

    private int state;
    private TTable<K, V> table;
    private K key;
    private V value;

    private CallPoint ownerCallPoint;

    /**
     * 过期设置的callPoint需要一个设置时间，防止DBService连接断开了，但是DBService还在运行存数据导致脏数据
     */
    private CallPoint willCallPoint;
    private long setWillCallPointMill;


    /**
     * 上一次访问时间
     */
    private volatile long lastAccessTime;


    public TRecord(TTable<K, V> table, K key, V value, int state, CallPoint ownerCallPoint) {
        this.table = table;
        this.key = key;
        this.value = value;
        this.state = state;
        this.ownerCallPoint = ownerCallPoint;
        value.makeModify();
        access();
    }


    /**
     * 这里肯定是线性的;因为只有add的地方调用，而且加锁了;
     */
    public void add(V newValue) {
        access();
        long oldDirty = value == null ? 0 : value.getDirty();
        state = ADD;
        value = newValue;
        // add 让其默认就是脏的 add也算一次变化 所以需要加一
        value.setDirty(oldDirty + 1);
    }

    /**
     * 这里肯定是线性的;因为只有remove的地方调用，而且加锁了
     */
    public void remove() {
        access();
        long oldDirty = value == null ? 0 : value.getDirty();
        state = REMOVE;
        value = table.newValue();
        // remove 让其默认就是脏的 remove也算一次变化 所以需要加一
        value.setDirty(oldDirty + 1);
    }

    public V get() {
        access();
        return value;
    }



    public void access() {
        lastAccessTime = System.nanoTime();
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public int getState() {
        return state;
    }

    public boolean isRemoveState(){
        return state == REMOVE;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public TTable<K, V> getTable() {
        return table;
    }

    public CallPoint getOwnerCallPoint() {
        return ownerCallPoint;
    }


    public void clearCallPoint() {
        this.ownerCallPoint = null;
    }

    public void setWillCallPoint(CallPoint willCallPoint) {
        if (willCallPoint == null) {
            return;
        }
        this.willCallPoint = willCallPoint;
        this.setWillCallPointMill = Service.getTime();
    }

    public void checkWillCallPoint(long currTime) {
        if (willCallPoint == null) {
            return;
        }
        if (currTime < setWillCallPointMill + TimeUtils.MIN * 5) {
            return;
        }
        ownerCallPoint = willCallPoint;
        setWillCallPointMill = 0;
        willCallPoint = null;
        logger.info("重新设置callPoint table {} key {} callPoint {} ", table.getName(), key, ownerCallPoint);
    }

    public boolean checkCallPoint(CallPoint callPoint) {
        return Objects.equals(callPoint, this.ownerCallPoint);
    }

    /**
     * todo 可能对比json  可能直接在有修改的时候弄个标记传播上来 然后直接判断那个标记
     * 什么时候重新计算hash;
     *      select成功的时候;
     *      保存到db的时候;保存到db分为
     *          checkPoint; 再
     *          flush;
     *          clearCache;
     */
    public boolean isModified() {
        // 下面这个不能有 特别是remove状态，因为checkpoint同步过以后，remove状态没法切到别的状态;
        // 如果通过REMOVE判断是否修改过，会导致每次checkpoint都会将其标记为脏，每次都同步，用checkModify()就没事了;
       /* switch (getState()) {
            case ADD:
                return true;
            case REMOVE:
                return true;
        }*/
        return value.checkModify();
    }


    public void checkpointRefreshState(Long oldDirty) {
        // 不一样，说明checkpoint协程让开期间，值又变过了;
        if (value.getDirty() != oldDirty) {
            return;
        }
        // 设置为没变化
        value.clearModify();

        switch (getState()) {
            case ADD:
                state = GET;
                break;
        }
    }

    public void checkPointFail() {
        // 失败了 直接标识已经修改过了，下次继续同步
        value.makeModify();
    }


    boolean flush(String reason) {
        try {
            switch (state) {
                case ADD:
                    return table.dbExec(table.createSaveDBReq(key, value), ownerCallPoint);
                case REMOVE:
                    return table.dbExec(table.createRemoveDBReq(key), ownerCallPoint);
                case GET:
                    // 对于GET状态，需要检查version条件
                    return table.dbExecWithVersionCheck(table.createSaveDBReq(key, value), ownerCallPoint, value.getDirty());
            }
        } catch (Exception e) {
            // 如果flush时候报错，则清除标识，下次会继续同步
            logger.error("TRecord flush error reason {} ", reason, e);
            return false;
        } finally {

        }
        return true;
    }

}
