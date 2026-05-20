package org.evd.game.common.location;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;

/**
 * 旧名字保留成薄壳，内部直接走 ET 风格的 MessageLocationSender。
 */
public class ActorLocationSender {
    private final MessageLocationSender messageLocationSender = new MessageLocationSender();

    public ActorAddress get(long actorId) {
        return messageLocationSender.get(ActorId.player(actorId));
    }

    public ActorAddress getOrQuery(long actorId) {
        return messageLocationSender.getOrQuery(ActorId.player(actorId));
    }

    public void cache(long actorId, ActorAddress actorAddress) {
        messageLocationSender.cache(ActorId.player(actorId), actorAddress);
    }

    public void remove(long actorId) {
        messageLocationSender.remove(ActorId.player(actorId));
    }

    public ActorAddress refresh(long actorId) {
        return messageLocationSender.refresh(ActorId.player(actorId));
    }

    public void send(long actorId, int methodKey, Object[] params) {
        messageLocationSender.send(ActorId.player(actorId), methodKey, params);
    }

    public <T> T callWait(long actorId, int methodKey, Object[] params) {
        return messageLocationSender.callWait(ActorId.player(actorId), methodKey, params);
    }

    public <T> T callWait(long actorId, int methodKey, Object[] params, long timeoutMillis) {
        return messageLocationSender.callWait(ActorId.player(actorId), methodKey, params, timeoutMillis);
    }
}
