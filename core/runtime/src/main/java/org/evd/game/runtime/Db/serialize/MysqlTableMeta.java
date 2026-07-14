package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.List;

/**
 * mysql 表结构元数据，只在初始化阶段透传。
 */
@SerializeClass
public class MysqlTableMeta implements ISerializable {
    private String keyColumnName;
    private List<String> columnNames;

    public String getKeyColumnName() {
        return keyColumnName;
    }

    public void setKeyColumnName(String keyColumnName) {
        this.keyColumnName = keyColumnName;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public void setColumnNames(List<String> columnNames) {
        this.columnNames = columnNames;
    }
}
