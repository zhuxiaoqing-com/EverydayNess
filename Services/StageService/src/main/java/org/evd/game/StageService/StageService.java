package org.evd.game.StageService;

import org.evd.game.StageService.disconnect.StageServiceConnectLogic;
import org.evd.game.StageService.disconnect.StageServiceDisconnectLogic;
import org.evd.game.StageService.mapCreate.StageSceneLogic;
import org.evd.game.common.proxy.LocationService.LocationServiceRpcProxy;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

/** StageService 服务本体，只负责服务生命周期。 */
public class StageService extends Service {
    public StageService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    public void tick() {
        getActor(StageSceneLogic.class).tick(getTime());
    }

    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> services) {
        getActor(StageServiceDisconnectLogic.class).onServiceDisconnect(services);
    }

    @Override
    protected void onServiceConnectReady(Collection<RegisteredService> services) {
        getActor(StageServiceConnectLogic.class).onServiceConnectReady(services);
    }

    public ActorAddress registerMapPlayerActor(long playerId) {
        ActorId actorId = ActorId.mapPlayer(playerId);
        boolean created = false;
        if (!hasActor(actorId)) {
            registerActorWithoutLocation(actorId, MailBoxType.UNORDERED);
            created = true;
        }
        ActorAddress actorAddress = getActorAddress(actorId);
        if (created) {
            var result = LocationServiceRpcProxy.sendAdd(null, actorId, actorAddress);
            if (!result.isSuccess()) {
                LogCore.core.warn("StageService 发送 MapPlayer ActorAddress 失败: service={}, playerId={}, actorAddress={}, errorCode={}, message={}",
                        getId(), playerId, actorAddress, result.getErrorCode(), result.getErrorMessage());
            }
        }
        return actorAddress;
    }

    public ActorAddress getMapPlayerActorAddress(long playerId) {
        return getActorAddress(ActorId.mapPlayer(playerId));
    }

    public ActorAddress unregisterMapPlayerActor(long playerId) {
        ActorId actorId = ActorId.mapPlayer(playerId);
        if (!hasActor(actorId)) {
            return null;
        }
        ActorAddress actorAddress = getActorAddress(actorId);
        var result = LocationServiceRpcProxy.sendRemove(null, actorId, actorAddress);
        if (!result.isSuccess()) {
            LogCore.core.warn("StageService 删除 MapPlayer ActorAddress 失败: service={}, playerId={}, actorAddress={}, errorCode={}, message={}",
                    getId(), playerId, actorAddress, result.getErrorCode(), result.getErrorMessage());
        }
        unregisterActorWithoutLocation(actorId);
        return actorAddress;
    }
}
