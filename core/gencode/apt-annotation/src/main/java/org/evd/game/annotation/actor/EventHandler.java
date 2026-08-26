package org.evd.game.annotation.actor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记允许实现运行时事件监听接口的 Actor 类。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface EventHandler {
}

