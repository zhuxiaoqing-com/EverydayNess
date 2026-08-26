package org.evd.game.annotation.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为指定的配置字段类型注册默认转换器。 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ConfigConverterFor {
    Class<?> value();
}
