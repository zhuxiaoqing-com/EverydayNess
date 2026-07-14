package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;

/**
 * 通用字段值包装，按 type 选择实际使用的值字段。
 */
@SerializeClass
public class ObjectValue implements ISerializable {
    /** 当前值的实际类型。 */
    private DbValueType type;
    /** long 类型值。 */
    private long longValue;
    /** int 类型值。 */
    private int intValue;
    /** String 类型值。 */
    private String stringValue;
    /** bytes 类型值。 */
    private byte[] bytesValue;
    /** boolean 类型值。 */
    private boolean booleanValue;
    /** double 类型值。 */
    private double doubleValue;

    public Object getV() {
        return switch (type) {
            case LONG -> getLongValue();
            case INT -> getIntValue();
            case STRING -> getStringValue();
            case BYTES -> getBytesValue();
            case BOOLEAN -> isBooleanValue();
            case DOUBLE -> getDoubleValue();
        };
    }

    public DbValueType getType() {
        return type;
    }

    public void setType(DbValueType type) {
        this.type = type;
    }

    public long getLongValue() {
        return longValue;
    }

    public void setLongValue(long longValue) {
        this.longValue = longValue;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    public byte[] getBytesValue() {
        return bytesValue;
    }

    public void setBytesValue(byte[] bytesValue) {
        this.bytesValue = bytesValue;
    }

    public boolean isBooleanValue() {
        return booleanValue;
    }

    public void setBooleanValue(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    public void setDoubleValue(double doubleValue) {
        this.doubleValue = doubleValue;
    }
}
