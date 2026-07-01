package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.base.InputStreamBase;
import org.evd.game.base.OutputStreamBase;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * 通用字段值包装，按 type 选择实际使用的值字段。
 */
@SerializeClass(customized = true)
public class DbValue implements ISerializable {
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

    public DbValue() {
    }

    public DbValue(Object value) {
        setValue(value);
    }

    public void setValue(Object value) {
        this.type = null;
        this.longValue = 0L;
        this.intValue = 0;
        this.stringValue = null;
        this.bytesValue = null;
        this.booleanValue = false;
        this.doubleValue = 0D;
        if (value == null) {
            throw new IllegalArgumentException("DbValue value 不能为空");
        }
        if (value instanceof Long longValue) {
            this.type = DbValueType.LONG;
            this.longValue = longValue;
            return;
        }
        if (value instanceof Integer intValue) {
            this.type = DbValueType.INT;
            this.intValue = intValue;
            return;
        }
        if (value instanceof String stringValue) {
            this.type = DbValueType.STRING;
            this.stringValue = stringValue;
            return;
        }
        if (value instanceof byte[] bytesValue) {
            this.type = DbValueType.BYTES;
            this.bytesValue = bytesValue;
            return;
        }
        if (value instanceof Boolean booleanValue) {
            this.type = DbValueType.BOOLEAN;
            this.booleanValue = booleanValue;
            return;
        }
        if (value instanceof Double doubleValue) {
            this.type = DbValueType.DOUBLE;
            this.doubleValue = doubleValue;
            return;
        }
        if (value instanceof Short shortValue) {
            this.type = DbValueType.INT;
            this.intValue = shortValue.intValue();
            return;
        }
        if (value instanceof Byte byteValue) {
            this.type = DbValueType.INT;
            this.intValue = byteValue.intValue();
            return;
        }
        if (value instanceof Float floatValue) {
            this.type = DbValueType.DOUBLE;
            this.doubleValue = floatValue.doubleValue();
            return;
        }
        throw new IllegalArgumentException("DbValue 不支持的类型: " + value.getClass().getName());
    }

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

    @Override
    public void writeTo(OutputStreamBase out) throws IOException {
        if (type == null) {
            throw new IllegalStateException("DbValue.type 不能为空");
        }
        out.write(type);
        switch (type) {
            case LONG -> out.writeLong(longValue);
            case INT -> out.writeInt(intValue);
            case STRING -> out.writeString(stringValue);
            case BYTES -> {
                if (!(out instanceof OutputStream outputStream)) {
                    throw new IllegalStateException("DbValue BYTES 序列化要求 runtime OutputStream");
                }
                outputStream.writeByteArray(bytesValue);
            }
            case BOOLEAN -> out.writeBoolean(booleanValue);
            case DOUBLE -> out.writeDouble(doubleValue);
        }
    }

    @Override
    public void readFrom(InputStreamBase in) throws IOException {
        this.type = in.read();
        this.longValue = 0L;
        this.intValue = 0;
        this.stringValue = null;
        this.bytesValue = null;
        this.booleanValue = false;
        this.doubleValue = 0D;
        switch (type) {
            case LONG -> this.longValue = in.readLong();
            case INT -> this.intValue = in.readInt();
            case STRING -> this.stringValue = in.readString();
            case BYTES -> {
                if (!(in instanceof InputStream inputStream)) {
                    throw new IllegalStateException("DbValue BYTES 反序列化要求 runtime InputStream");
                }
                this.bytesValue = inputStream.readByteArray();
            }
            case BOOLEAN -> this.booleanValue = in.readBoolean();
            case DOUBLE -> this.doubleValue = in.readDouble();
        }
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


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DbValue dbValue = (DbValue) o;
        if (type != dbValue.type) {
            return false;
        }

        return switch (type) {
            case LONG -> longValue == dbValue.longValue;
            case INT -> intValue == dbValue.intValue;
            case STRING -> Objects.equals(stringValue, dbValue.stringValue);
            case BYTES -> Arrays.equals(bytesValue, dbValue.bytesValue);
            case BOOLEAN -> booleanValue == dbValue.booleanValue;
            case DOUBLE -> Double.compare(doubleValue, dbValue.doubleValue) == 0;
        };
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case LONG -> Objects.hash(type, longValue);
            case INT -> Objects.hash(type, intValue);
            case STRING -> Objects.hash(type, stringValue);
            case BYTES -> 31 * Objects.hash(type) + Arrays.hashCode(bytesValue);
            case BOOLEAN -> Objects.hash(type, booleanValue);
            case DOUBLE -> Objects.hash(type, doubleValue);
        };
    }

    @Override
    public String toString() {
        if (type == null) {
            return "DbValue{type=null}";
        }
        return switch (type) {
            case BYTES -> Arrays.toString(bytesValue);
            default -> String.valueOf(getV());
        };
    }
}
