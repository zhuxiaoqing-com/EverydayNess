package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.List;

/**
 * 通用字段值包装，按 type 选择实际使用的值字段。
 */
@SerializeClass
public class DbTableField implements ISerializable {
    /** 当前值的实际类型。 */
    private List<DbValue> valueList;
    /** 行在 MDB 中的持久化版本，与业务列分开传输。 */
    private long version;


    public DbTableField() {
    }

    public DbTableField(List<DbValue> valueList) {
        this.valueList = valueList;
    }

    public List<DbValue> getValueList() {
        return valueList;
    }

    public void setValueList(List<DbValue> valueList) {
        this.valueList = valueList;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Object getTableKey() {
        return getValueList().get(0).getV();
    }


}
