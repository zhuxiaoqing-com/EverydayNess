package org.evd.game.runtime.Db.table;

import org.evd.game.base.DirtyObject;
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
     * 上一次访问时间
     */
    private volatile long lastAccessTime;


    public TRecord(TTable<K, V> table, K key, V value, int state, CallPoint ownerCallPoint) {
        this.table = table;
        this.key = key;
        this.value = value;
        this.state = state;
        this.ownerCallPoint = ownerCallPoint;
        access();
    }


    /**
     * 这里肯定是线性的;因为只有add的地方调用，而且加锁了;
     */
    public void add(V newValue) {
        access();
        state = ADD;
        value = newValue;
    }

    /**
     * 这里肯定是线性的;因为只有remove的地方调用，而且加锁了
     */
    public void remove() {
        access();
        state = REMOVE;
        value = table.newValue();
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

    /**
     * 只有dbService缩容的时候才能用到; 扩容已有的数据不管;只有新数据才会分配过去;
     *
     * @param ownerCallPoint
     */
    public void setOwnerCallPoint(CallPoint ownerCallPoint) {
        this.ownerCallPoint = ownerCallPoint;
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
        // 这里不用单独在判断 add remove; 因为在new对象的时候已经将version自增了
        switch (getState()) {
            case ADD:
                return true;
            case REMOVE:
                return true;
        }
        return ((DirtyObject) value).checkModify();
    }


    public void checkpointRefreshState(int _state) {
        // 设置为没变化
        value.makeModify();

        switch (_state) {
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
                    return table.dbExec(table.createSaveDBReq(value), ownerCallPoint);
                case REMOVE:
                    return table.dbExec(table.createRemoveDBReq(key), ownerCallPoint);
                case GET:
                    return table.dbExec(table.createSaveDBReq(value), ownerCallPoint);
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
