package org.evd.game.Db.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

/**
 * @author zhuxiaoqing
 * @Description: MysqlData
 * @Date 2026/5/26 20:38
 **/
@SerializeClass
public class DBRsp implements ISerializable {


    @SerializeField
    boolean success;
    @SerializeField
    String exceptionMessage;
    /**
     * 操作加上描述表里的字段，可以拼接成sql
     */
    @SerializeField
    MysqlRsp mysqlRsp;


    public DBRsp() {
    }

    public DBRsp(String exceptionMessage) {
        this.success = false;
        this.exceptionMessage = exceptionMessage;
    }

    public MysqlRsp getMysqlRsp() {
        return mysqlRsp;
    }

    public void setMysqlRsp(MysqlRsp mysqlRsp) {
        this.mysqlRsp = mysqlRsp;
    }


    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }
}

