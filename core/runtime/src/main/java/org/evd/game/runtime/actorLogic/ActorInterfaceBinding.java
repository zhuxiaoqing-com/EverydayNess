package org.evd.game.runtime.actorLogic;

/**
 * 接口归类第一步产生的中间结构。
 * 只在接口索引构建链路里流转，不对外暴露成独立领域对象。
 */
public record ActorInterfaceBinding(Class<?> actorClass, Object actor) {
}
