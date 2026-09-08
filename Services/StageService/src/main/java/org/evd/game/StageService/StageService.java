package org.evd.game.StageService;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

/** StageService 服务本体，只负责服务生命周期。 */
public class StageService extends Service {
    public StageService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    public ActorAddress registerMapPlayerActor(long playerId) {
        ActorId actorId = ActorId.mapPlayer(playerId);
        if (!hasActor(actorId)) {
            registerActorWithoutLocation(actorId, MailBoxType.UNORDERED);
        }
        return getActorAddress(actorId);
    }

    public ActorAddress unregisterMapPlayerActor(long playerId) {
        ActorId actorId = ActorId.mapPlayer(playerId);
        if (!hasActor(actorId)) {
            return null;
        }
        ActorAddress actorAddress = getActorAddress(actorId);
        unregisterActorWithoutLocation(actorId);
        return actorAddress;
    }
}
