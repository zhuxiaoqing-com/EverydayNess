package org.evd.game.annotation.serialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记不参与自动序列化的字段或 record 组件。
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface SerializeIgnore {
}

