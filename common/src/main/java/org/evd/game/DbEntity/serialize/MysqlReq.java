package org.evd.game.DbEntity.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

import java.util.List;

/**
 * @author zhuxiaoqing
 * @Description: MysqlData
 * @Date 2026/5/26 20:38
 **/
@SerializeClass
public class MysqlReq implements ISerializable {
    @SerializeField
    private String sql;
    @SerializeField
    private String tableName;
    @SerializeField
    private List<DbTableField> tablFieldList;


    public DbTableField getSingleTableField() {
        return getTablFieldList().get(0);
    }

    public Object getSingleTableKey() {
        return getTableKey(getSingleTableField());
    }

    public Object getTableKey(DbTableField tableField) {
        return tableField.getValueList().get(0).getV();
    }



    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
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
