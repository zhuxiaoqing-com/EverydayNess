package org.evd.game.annotation.config;

/** 将一个 CSV 字段值转换成 Java 类型值。 */
@FunctionalInterface
public interface ConfigConverter<T> {
    T convert(CharSequence value);
}
