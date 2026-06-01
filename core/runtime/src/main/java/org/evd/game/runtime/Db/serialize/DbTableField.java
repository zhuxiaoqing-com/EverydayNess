package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

import java.util.List;

/**
 * 通用字段值包装，按 type 选择实际使用的值字段。
 */
@SerializeClass
public class DbTableField implements ISerializable {
    /** 当前值的实际类型。 */
    @SerializeField
    private List<DbValue> valueList;


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
}
