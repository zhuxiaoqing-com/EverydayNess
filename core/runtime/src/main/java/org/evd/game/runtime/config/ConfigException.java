package org.evd.game.runtime.config;

/** 配置加载和校验失败时使用的基础异常。 */
public class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
