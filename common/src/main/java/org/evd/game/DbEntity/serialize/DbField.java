package org.evd.game.DbEntity.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

/**
 * MySQL 行中的单个字段值。
 */
@SerializeClass
public class DbField implements ISerializable {
    /** 字段名。 */
    @SerializeField
    private String name;
    /** 字段值。 */
    @SerializeField
    private DbValue value;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DbValue getValue() {
        return value;
    }

    public void setValue(DbValue value) {
        this.value = value;
    }

}
