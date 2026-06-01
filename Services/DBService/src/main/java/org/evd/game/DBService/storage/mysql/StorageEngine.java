package org.evd.game.DBService.storage.mysql;


import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;

/**
 * insert
 * replace
 * replaceBatch
 * remove
 * removeBatch
 * 的时候如果报错了 就将数据记录下来,放到一个线程安全的列表里面,定时检测数据库是否连接正常, 长度大于n以后淘汰;并且输出日志
 */
public interface StorageEngine {
    DBRsp find(DBReq _dbReq);

    DBRsp findBatch(DBReq _dbReq);

    boolean insert(DBReq _dbReq);

    void replace(DBReq _dbReq);

    void replaceBatch(DBReq _dbReq);

    void remove(DBReq _dbReq);

     void removeBatch(DBReq _dbReq);

    boolean detect();

    void initTable(DBReq _dbReq);

    void close();
}
