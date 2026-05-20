package org.evd.game.common.location;

import org.evd.game.common.proxy.HaHaHaActorProxy;

/**
 * 演示 ET 风格 actor-location 调用链：
 * 先用 actorId 找缓存的宿主，命中失败后清缓存并重新查 location，再重试。
 */
public class HaHaHaActorLocationSender {
    private final MessageLocationSender messageLocationSender = new MessageLocationSender();

    public void rpc1(long actorId, int a, int b) {
        messageLocationSender.send(org.evd.game.runtime.actor.ActorId.player(actorId),
                HaHaHaActorProxy.EnumCall.ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT,
                new Object[]{a, b});
    }

    public void rpc2(long actorId, Object a, Object b) {
        messageLocationSender.send(org.evd.game.runtime.actor.ActorId.player(actorId),
                HaHaHaActorProxy.EnumCall.ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT,
                new Object[]{a, b});
    }
}
