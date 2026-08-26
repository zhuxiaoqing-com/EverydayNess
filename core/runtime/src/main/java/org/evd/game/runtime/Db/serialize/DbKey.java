package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.base.InputStreamBase;
import org.evd.game.base.OutputStreamBase;

import java.io.IOException;

/**
 * 通用 DB 主键表达，按 type 选择实际使用的值字段。
 */
@SerializeClass(customized = true)
public class DbKey implements ISerializable {
    /** 主键值类型。 */
    private DbKeyType type;
    /** long 类型主键值。 */
    private long longValue;
    /** int 类型主键值。 */
    private int intValue;
    /** String 类型主键值。 */
    private String stringValue;

    public DbKey() {
    }

    public DbKey(Object value) {
        setValue(value);
    }

    public void setValue(Object value) {
        this.type = null;
        this.longValue = 0L;
        this.intValue = 0;
        this.stringValue = null;
        if (value == null) {
            throw new IllegalArgumentException("DbKey value 不能为空");
        }
        if (value instanceof Long longValue) {
            this.type = DbKeyType.LONG;
            this.longValue = longValue;
            return;
        }
        if (value instanceof Integer intValue) {
            this.type = DbKeyType.INT;
            this.intValue = intValue;
            return;
        }
        if (value instanceof String stringValue) {
            this.type = DbKeyType.STRING;
            this.stringValue = stringValue;
            return;
        }
        throw new IllegalArgumentException("DbKey 不支持的类型: " + value.getClass().getName());
    }


    public Object getV() {
        return switch (type) {
            case LONG -> getLongValue();
            case INT -> getIntValue();
            case STRING -> getStringValue();
        };
    }


    /**
     * 将自己写入流中
     *
     * @param out OutputStream
     * @throws IOException IOException
     */
    @Override
    public void writeTo(OutputStreamBase out) throws IOException {
        if (type == null) {
            throw new IllegalStateException("DbKey.type 不能为空");
        }
        out.write(type);
        switch (type) {
            case LONG -> out.writeLong(longValue);
            case INT -> out.writeInt(intValue);
            case STRING -> out.writeString(stringValue == null ? "" : stringValue);
        }
    }

    /**
     * 从流中读取
     *
     * @param in InputStream
     * @throws IOException IOException
     */
    @Override
    public void readFrom(InputStreamBase in) throws IOException {
        this.type = in.read();
        this.longValue = 0L;
        this.intValue = 0;
        this.stringValue = null;
        switch (type) {
            case LONG -> this.longValue = in.readLong();
            case INT -> this.intValue = in.readInt();
            case STRING -> this.stringValue = in.readString();
        }
    }

    @Override
    public String toString() {
        return getV().toString();
    }

    public DbKeyType getType() {
        return type;
    }

    public void setType(DbKeyType type) {
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

}
