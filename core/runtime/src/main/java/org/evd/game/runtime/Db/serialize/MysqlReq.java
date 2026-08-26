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
public class MysqlReq implements ISerializable {
    private String sql;
    private String tableName;
    private MysqlTableMeta tableMeta;
    private List<DbTableField> tablFieldList = new ArrayList<>();


    public DbTableField getSingleTableField() {
        return getTablFieldList().get(0);
    }

    public Object getSingleTableKey() {
        return getTableKey(getSingleTableField());
    }

    public Object getTableKey(DbTableField tableField) {
        return tableField.getTableKey();
    }



    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public MysqlTableMeta getTableMeta() {
        return tableMeta;
    }

    public void setTableMeta(MysqlTableMeta tableMeta) {
        this.tableMeta = tableMeta;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<DbTableField> getTablFieldList() {
        return tablFieldList;
    }

    public void setTablFieldList(List<DbTableField> tablFieldList) {
        this.tablFieldList = tablFieldList;
    }


}
