package org.evd.game.runtime.config;

import java.util.Arrays;

/** 生成的配置访问类使用的内部复合 Key。 */
final class ConfigKey {
    private final Object[] values;
    private final int hashCode;

    private ConfigKey(Object[] values) {
        this.values = values;
        this.hashCode = Arrays.deepHashCode(values);
    }

    static ConfigKey of(Object... values) {
        return new ConfigKey(Arrays.copyOf(values, values.length));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfigKey key && Arrays.deepEquals(values, key.values);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
