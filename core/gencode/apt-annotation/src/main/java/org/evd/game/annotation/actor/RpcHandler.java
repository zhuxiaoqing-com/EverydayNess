package org.evd.game.annotation.actor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记允许声明 RPC 方法的处理类，可声明其代理需要实现的接口。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface RpcHandler {
    /**
     * 生成的 RPC 代理需要实现的接口。接口中的抽象方法必须由当前 Handler 的 {@link Rpc} 方法覆盖；
     * 默认 {@code void.class} 表示不绑定接口。
     */
    Class<?> value() default void.class;
}

