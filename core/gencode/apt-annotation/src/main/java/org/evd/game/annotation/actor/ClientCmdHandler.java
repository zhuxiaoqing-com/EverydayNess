package org.evd.game.annotation.actor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记允许声明 {@link ClientCmd} 方法的处理类。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ClientCmdHandler {
}

