package org.evd.game.DBService.entity;

import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.Db.serialize.DbTableField;
import org.evd.game.runtime.Db.serialize.DbValue;

import java.util.List;

/**
 * @author zhuxiaoqing
 * @Description: TRecord
 * @Date 2026/7/1 15:36
 **/
public class MysqlTRecord implements TRecord {
    public static long TICK_VERSION = 0;
    DbOpType dbOpType;
    DbTableField dbTableField;
    DbValue ObjKey;
    Object key;
    List<DbValue> value;
    public long tickVersion;

    public MysqlTRecord(DbOpType dbOpType, DbTableField dbTableField) {
        this.tickVersion = TICK_VERSION++;
        this.dbTableField = dbTableField;
        this.dbOpType = dbOpType;
        this.value = dbTableField.getValueList();
        this.key = dbTableField.getTableKey();
    }

    public DbOpType getDbOpType() {
        return dbOpType;
    }

    public DbValue getObjKey() {
        return ObjKey;
    }

    @Override
    public Object getKey() {
        return key;
    }

    public List<DbValue> getValue() {
        return value;
    }

    public DbTableField getDbTableField() {
        return dbTableField;
    }

    @Override
    public long tickVersion() {
        return tickVersion;
    }

    public long getTickVersion() {
        return tickVersion;
    }
}
