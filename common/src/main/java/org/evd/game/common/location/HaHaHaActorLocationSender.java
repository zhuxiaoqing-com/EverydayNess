package org.evd.game.common.location;

import org.evd.game.common.proxy.StageServiceActorLocationProxy;

/**
 * 演示 ET 风格 actor-location 调用链：
 * 先用 actorId 找缓存的宿主，命中失败后清缓存并重新查 location，再重试。
 */
public class HaHaHaActorLocationSender {
    private final MessageLocationSender messageLocationSender = new MessageLocationSender();

    public void rpc1(long actorId, int a, int b) {
        messageLocationSender.callWithRetry(actorId,
                callPoint -> {
                    StageServiceActorLocationProxy.inst(callPoint).callHaHaHaActorRpc1(actorId, a, b);
                    return null;
                });
    }

    public void rpc2(long actorId, Object a, Object b) {
        messageLocationSender.callWithRetry(actorId,
                callPoint -> {
                    StageServiceActorLocationProxy.inst(callPoint).callHaHaHaActorRpc2(actorId, a, b);
                    return null;
                });
    }
}
