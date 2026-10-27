package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhuxiaoqing
 * @Description: MysqlData
 * @Date 2026/5/26 20:38
 **/
@SerializeClass
public class DBReq implements ISerializable {

    private DbOpType dbOpType;

    MysqlReq mysqlReq = new MysqlReq();
    private List<DBReq> transactionRequests = new ArrayList<>();


    public DBReq() {
    }

    public DBReq(MysqlReq mysqlReq) {
        this.mysqlReq = mysqlReq;
    }

    public MysqlReq getMysqlReq() {
        return mysqlReq;
    }

    public void setMysqlReq(MysqlReq mysqlReq) {
        this.mysqlReq = mysqlReq;
    }

    public DbOpType getDbOpType() {
        return dbOpType;
    }

    public void setDbOpType(DbOpType dbOpType) {
        this.dbOpType = dbOpType;
    }

    public List<DBReq> getTransactionRequests() {
        return transactionRequests;
    }

    public void setTransactionRequests(List<DBReq> transactionRequests) {
        this.transactionRequests = transactionRequests;
    }
}

