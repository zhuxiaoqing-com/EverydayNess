package org.evd.game.DbEntity.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

/**
 * @author zhuxiaoqing
 * @Description: MysqlData
 * @Date 2026/5/26 20:38
 **/
@SerializeClass
public class DBReq implements ISerializable {

    @SerializeField
    private DbOpType dbOpType;

    @SerializeField
    MysqlReq mysqlReq = new MysqlReq();


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
}

