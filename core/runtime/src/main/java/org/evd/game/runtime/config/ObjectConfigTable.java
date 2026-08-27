package org.evd.game.runtime.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 非 int 单 Key 和复合 Key 使用的对象表。 */
final class ObjectConfigTable implements ConfigTable {
    private final Map<Object, Object> values = new LinkedHashMap<>();

    @Override
    public Object get(Object key) {
        return values.get(key);
    }

    @Override
    public Object getInt(int key) {
        return values.get(key);
    }

    @Override
    public void put(Object key, Object value, Class<?> type, String file, long line) {
        if (values.putIfAbsent(key, value) != null) {
            throw duplicate(type, file, line, key);
        }
    }

    @Override
    public void putInt(int key, Object value, Class<?> type, String file, long line) {
        put(key, value, type, file, line);
    }

    @Override
    public Collection<Object> values() {
        return Collections.unmodifiableCollection(values.values());
    }

    private static ConfigException duplicate(Class<?> type, String file, long line, Object key) {
        return new ConfigException("Config duplicate key: file=" + file
                + ", config=" + type.getName() + ", line=" + line + ", key=" + key);
    }
}
