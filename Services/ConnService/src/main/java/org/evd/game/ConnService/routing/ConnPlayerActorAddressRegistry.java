package org.evd.game.ConnService.routing;

import org.evd.game.ConnService.ConnService;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;

/** ConnService 的玩家 ActorAddress 注册和 LocationService 缓存。 */
public final class ConnPlayerActorAddressRegistry {
    private final ConnService owner;

    public ConnPlayerActorAddressRegistry(ConnService owner) {
        this.owner = owner;
    }

    /** 缓存当前玩家 PlayerService ActorAddress。 */
    public void cachePlayerActorAddress(long playerId, ActorAddress playerActorAddress) {
        if (playerId <= 0L || playerActorAddress == null) {
            return;
        }
        ActorId actorId = ActorId.player(playerId);
        owner.getMessageLocationSender().cache(actorId, playerActorAddress);
        LogCore.core.info("ConnService 缓存 PlayerActorAddress: playerId={}, actorId={}, actorAddress={}",
                playerId, actorId, playerActorAddress);
    }

    /** 缓存当前玩家所在 Stage 的 ActorAddress。 */
    public boolean cacheStageActorAddress(long playerId, ActorAddress stageActorAddress) {
        if (playerId <= 0L || stageActorAddress == null) {
            return false;
        }
        owner.getMessageLocationSender().cache(ActorId.mapPlayer(playerId), stageActorAddress);
        return true;
    }

    /** 删除当前玩家所在 Stage 的 ActorAddress 缓存。 */
    public void removeStageActorAddress(long playerId) {
        owner.getMessageLocationSender().remove(ActorId.mapPlayer(playerId));
    }

}
