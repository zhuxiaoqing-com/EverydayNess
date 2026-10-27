package org.evd.game.runtime.Db.table;

import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.serialize.DBReq;
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
        if (state != GET) {
            value.makeModify();
        }
        access();
    }


    /** 仅由 TTable 在同步逻辑片段中调用；状态切换本身不包含协程等待。 */
    public void remove() {
        access();
        long oldMemVersion = value == null ? 0 : value.getMemVersion();
        long oldDbVersion = value == null ? 0 : value.getDbVersion();
        state = REMOVE;
        // 删除标记只保留主键，供对象版立即同步入口定位记录。
        value = table.newRemoveValue(key);
        value.setMemVersion(oldMemVersion + 1);
        value.setDbVersion(oldDbVersion);
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

    DBReq createSyncRequest() {
        return table.createSyncRequest(this);
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

    public boolean isModified() {
        return value.isModified();
    }

    public boolean checkpointRefreshState(long snapshotMemVersion) {
        // 只确认已经成功保存的快照；期间的新修改会让 memVersion 保持更大。
        value.setDbVersion(Math.max(value.getDbVersion(), snapshotMemVersion));
        if (value.getMemVersion() != snapshotMemVersion) {
            return false;
        }

        switch (getState()) {
            case ADD:
                state = GET;
                break;
        }

        return true;
    }

    boolean flush(String reason) {
        try {
            switch (state) {
                case ADD:
                    return table.dbExec(table.createSaveDBReq(key, value), ownerCallPoint);
                case REMOVE:
                    return table.dbExec(table.createRemoveDBReq(key), ownerCallPoint);
                case GET:
                    return table.dbExec(table.createSaveDBReq(key, value), ownerCallPoint);
            }
        } catch (Exception e) {
            // 保存失败时未确认当前版本，下次仍会继续同步。
            logger.error("TRecord flush error reason {} ", reason, e);
            return false;
        } finally {

        }
        return true;
    }

}
