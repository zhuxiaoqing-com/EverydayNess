package org.evd.game.runtime.config;

/** 为字段转换失败补充配置表、行号、字段和原始值上下文。 */
public final class ConfigParseException extends ConfigException {
    private ConfigParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ConfigParseException forField(String file, long line, String column,
                                                String field, String type, CharSequence value,
                                                Throwable cause) {
        return new ConfigParseException("Config parse error: file=" + file
                + ", line=" + line + ", column=" + column + ", field=" + field
                + ", type=" + type + ", value=" + value, cause);
    }
}
