package org.evd.game.runtime.config;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 单 int Key 的 fastutil 配置表。 */
final class IntObjectTable implements ConfigTable {
    private final Int2ObjectOpenHashMap<Object> values = new Int2ObjectOpenHashMap<>();
    private final List<Object> orderedValues = new ArrayList<>();

    @Override
    public Object get(Object key) {
        return key instanceof Integer integer ? getInt(integer) : null;
    }

    @Override
    public Object getInt(int key) {
        return values.get(key);
    }

    @Override
    public void put(Object key, Object value, Class<?> type, String file, long line) {
        if (!(key instanceof Integer integer)) {
            throw new ConfigException("单 int Key 配置收到非 int Key: config=" + type.getName()
                    + ", key=" + key);
        }
        putInt(integer, value, type, file, line);
    }

    @Override
    public void putInt(int key, Object value, Class<?> type, String file, long line) {
        if (values.containsKey(key)) {
            throw new ConfigException("Config duplicate key: file=" + file
                    + ", config=" + type.getName() + ", line=" + line + ", key=" + key);
        }
        values.put(key, value);
        orderedValues.add(value);
    }

    @Override
    public Collection<Object> values() {
        return Collections.unmodifiableList(orderedValues);
    }
}
