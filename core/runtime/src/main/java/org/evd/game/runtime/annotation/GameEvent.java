package org.evd.game.runtime.annotation;

import java.lang.annotation.*;

@Repeatable(GameEvents.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameEvent {
    Class<? extends Event> event();

    Class<?>[] dependsOn() default {}; // 依赖的 actor 类型
}
