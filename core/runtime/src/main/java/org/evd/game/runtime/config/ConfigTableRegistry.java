package org.evd.game.runtime.config;

import org.evd.game.annotation.config.ConfigConverter;

import java.util.Map;

/** APT 生成的配置类型注册表。 */
public interface ConfigTableRegistry {
    Class<?>[] configTypes();

    Map<Class<?>, Class<? extends ConfigConverter<?>>> defaultConverters();
}
