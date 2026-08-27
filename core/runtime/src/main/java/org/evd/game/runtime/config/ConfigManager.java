package org.evd.game.runtime.config;

import org.evd.game.annotation.config.ConfigConverter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

/** 进程级原子配置存储，由所有生成的配置访问类共享。 */
public final class ConfigManager {
    private static final String CONFIG_TABLE_LOADER_CLASS_NAME =
            "org.evd.game.common.config.table.TableRegistry";
    private static volatile Map<Class<?>, ConfigTable> CONFIGS = Map.of();
    private static volatile Map<Class<?>, Class<? extends ConfigConverter<?>>> DEFAULT_CONVERTERS = Map.of();

    private ConfigManager() {
    }

    public static Object key(Object... values) {
        if (values.length == 1) {
            return values[0];
        }
        return ConfigKey.of(values);
    }

    static synchronized void replaceAll(Map<Class<?>, ConfigTable> tables) {
        CONFIGS = Map.copyOf(tables);
    }

    static synchronized void replaceLoaded(Map<Class<?>, ConfigTable> tables) {
        Map<Class<?>, ConfigTable> merged = new HashMap<>(CONFIGS);
        merged.putAll(tables);
        CONFIGS = Map.copyOf(merged);
    }

    public static synchronized <T> void register(Class<T> type) {
        Map<Class<?>, ConfigTable> tables = new HashMap<>(CONFIGS);
        tables.putIfAbsent(type, new ObjectConfigTable());
        CONFIGS = Map.copyOf(tables);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type, Object... keys) {
        ConfigTable table = CONFIGS.get(type);
        if (table == null) {
            return null;
        }
        return (T) table.get(key(keys));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInt(Class<T> type, int key) {
        ConfigTable table = CONFIGS.get(type);
        if (table == null) {
            return null;
        }
        return (T) table.getInt(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> Collection<T> getAll(Class<T> type) {
        ConfigTable table = CONFIGS.get(type);
        if (table == null) {
            return List.of();
        }
        return (Collection<T>) table.values();
    }

    public static synchronized void clear() {
        CONFIGS = Map.of();
        DEFAULT_CONVERTERS = Map.of();
        ConfigTableInitializer.reset();
    }

    /** 加载 APT 生成的全部公共 CSV 配置。 */
    public static synchronized void load(Path directory) {
        Map<Class<?>, Class<? extends ConfigConverter<?>>> oldConverters = DEFAULT_CONVERTERS;
        try {
            ConfigTableRegistry registry = Class.forName(CONFIG_TABLE_LOADER_CLASS_NAME)
                    .asSubclass(ConfigTableRegistry.class)
                    .getDeclaredConstructor()
                    .newInstance();
            DEFAULT_CONVERTERS = Map.copyOf(registry.defaultConverters());
            ReflectiveConfigTableLoader.load(directory, registry.configTypes());
        } catch (ReflectiveOperationException e) {
            DEFAULT_CONVERTERS = oldConverters;
            throw new ConfigException("Config table loader not found: class="
                    + CONFIG_TABLE_LOADER_CLASS_NAME, e);
        } catch (RuntimeException e) {
            DEFAULT_CONVERTERS = oldConverters;
            throw e;
        }
    }

    public static synchronized void load(Path directory, Class<?>... configTypes) {
        ReflectiveConfigTableLoader.load(directory, configTypes, false);
    }

    public static synchronized void registerDefaultConverter(Class<?> targetType,
                                                             Class<? extends ConfigConverter<?>> converterType) {
        Map<Class<?>, Class<? extends ConfigConverter<?>>> converters = new HashMap<>(DEFAULT_CONVERTERS);
        converters.put(targetType, converterType);
        DEFAULT_CONVERTERS = Map.copyOf(converters);
    }

    static Class<? extends ConfigConverter<?>> defaultConverter(Class<?> targetType) {
        return DEFAULT_CONVERTERS.get(targetType);
    }

    public static void load(String directory) {
        load(Path.of(directory));
    }

}
