package org.evd.game.annotation;

import org.evd.game.runtime.actor.ActorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Rpc {
    boolean http() default false;
    ActorType actorType() default ActorType.NONE;
}
