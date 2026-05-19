package org.evd.game.common.location;

import org.evd.game.common.actor.ActorSender;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorId;

/**
 * 兼容 ET 风格的 actor-location sender。
 */
public class ActorLocationSender {
    private final ActorSender actorSender = new ActorSender();

    public CallPoint get(long actorId) {
        return actorSender.player(actorId).get();
    }

    public CallPoint getOrQuery(long actorId) {
        return actorSender.player(actorId).getOrQuery();
    }

    public void cache(long actorId, CallPoint callPoint) {
        actorSender.player(actorId).cache(callPoint);
    }

    public void remove(long actorId) {
        actorSender.player(actorId).remove();
    }

    public CallPoint refresh(long actorId) {
        return actorSender.player(actorId).refresh();
    }

    public void send(long actorId, int methodKey, Object[] params) {
        actorSender.player(actorId).callWithRetry((callPoint, actorRef) -> {
            Service.getCurrent().locationCallWait(callPoint, actorRef, methodKey, params);
            return null;
        });
    }

    public <T> T callWait(long actorId, int methodKey, Object[] params) {
        return actorSender.player(actorId).callWithRetry((callPoint, actorRef) ->
                (T) Service.getCurrent().locationCallWait(callPoint, actorRef, methodKey, params));
    }

    public <T> T callWait(long actorId, int methodKey, Object[] params, long timeoutMillis) {
        return actorSender.player(actorId).callWithRetry((callPoint, actorRef) ->
                (T) Service.getCurrent().locationCallWait(callPoint, actorRef, methodKey, params, timeoutMillis));
    }

    public <T> T callWithRetry(long actorId, LocationCaller<T> caller) {
        return actorSender.player(actorId).callWithRetry((callPoint, actorRef) -> caller.call(callPoint));
    }

    public <T> T callWithRetry(long actorId, ActorLocationCaller<T> caller) {
        return actorSender.player(actorId).callWithRetry(caller::call);
    }

    @FunctionalInterface
    public interface LocationCaller<T> {
        T call(CallPoint callPoint);
    }

    @FunctionalInterface
    public interface ActorLocationCaller<T> {
        T call(CallPoint callPoint, ActorId actorId);
    }
}
