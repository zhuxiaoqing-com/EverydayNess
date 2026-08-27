package org.evd.game.runtime.config;

import java.util.Collection;

/** 配置表的统一存储接口，具体 Key 类型由实现选择。 */
interface ConfigTable {
    Object get(Object key);

    Object getInt(int key);

    void put(Object key, Object value, Class<?> type, String file, long line);

    void putInt(int key, Object value, Class<?> type, String file, long line);

    Collection<Object> values();
}
