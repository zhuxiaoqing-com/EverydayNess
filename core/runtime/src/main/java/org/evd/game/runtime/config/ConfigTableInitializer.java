package org.evd.game.runtime.config;

import java.nio.file.Path;
import java.util.Objects;

/** 由 Service 触发、负责配置表初始化和串行重载的生命周期入口。 */
public final class ConfigTableInitializer {
    /** 与 ConfigManager 的 synchronized API 使用同一把锁，保证 clear/init/reload 不交叉。 */
    private static final Object LOCK = ConfigManager.class;
    private static volatile boolean initialized;

    private ConfigTableInitializer() {
    }

    static void reset() {
        initialized = false;
    }

    public static void init(Path directory) {
        Objects.requireNonNull(directory, "directory");

        if (initialized) {
            return;
        }

        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            ConfigManager.load(directory.toAbsolutePath().normalize());
            initialized = true;
        }
    }

    /**
     * 在初始化锁内重载全部配置表，避免重载与初始化或其他重载并发执行。
     *
     * @param directory 配置文件目录
     */
    public static void reload(Path directory) {
        Objects.requireNonNull(directory, "directory");

        synchronized (LOCK) {
            ConfigManager.load(directory.toAbsolutePath().normalize());
            initialized = true;
        }
    }
}
