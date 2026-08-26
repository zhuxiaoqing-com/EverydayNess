package org.evd.game.annotation.serialize;

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
@Target({ElementType.FIELD})
public @interface DBDirtyTag {
    int value();
    boolean primaryKey() default false; // 只能是基础类型+String;如果DBDirtyEntity(table=true)的类没有标识primaryKey就报错;
}

