package org.evd.game.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Rpc {
    boolean http() default false;
    RpcRoute route() default RpcRoute.SERVICE;
    RpcActorType actorType() default RpcActorType.NONE;
}
