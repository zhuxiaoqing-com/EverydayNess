package org.evd.game.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 序列化字段注解
 * @author zenghongming
 * @date 2020/02/09 16:59
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface DBDirtyEntity {
    /**
     * 是否是数据库表
     */
    boolean table() default false;
}
