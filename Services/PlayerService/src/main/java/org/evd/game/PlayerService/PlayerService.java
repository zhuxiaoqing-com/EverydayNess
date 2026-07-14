package org.evd.game.PlayerService;

import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.LocationService.LocationServiceProxy;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.HashSet;
import java.util.Set;

public class PlayerService extends Service {
    private final Set<Long> onlinePlayerIds = new HashSet<>();

    public PlayerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Rpc
    public boolean bindPlayerSession(String userId, long playerId, ClientSessionRef session) {
        if (userId == null || userId.isBlank() || playerId <= 0L || session == null) {
            return false;
        }
        if (onlinePlayerIds.contains(playerId)) {
            removePlayerActor(playerId);
            onlinePlayerIds.remove(playerId);
        }

        registerPlayerActor(playerId);
        onlinePlayerIds.add(playerId);
        LogCore.core.info("PlayerService 绑定玩家: service={}, userId={}, playerId={}, sessionId={}",
                id, userId, playerId, session.getSessionId());
        return true;
    }

    @Rpc
    public void onPlayerOffline(String userId, long playerId, int brokenTypeCode) {
        if (!onlinePlayerIds.contains(playerId)) {
            return;
        }
        removePlayerActor(playerId);
        onlinePlayerIds.remove(playerId);
        LogCore.core.info("PlayerService 玩家离线: service={}, userId={}, playerId={}, brokenType={}",
                id, userId, playerId, BrokenType.fromCode(brokenTypeCode));
    }

    @Rpc
    public void enterMap(long playerId) {
        LogCore.core.info("PlayerService 进入地图占位: service={}, playerId={}", id, playerId);
    }

    @Rpc
    public int getOnlineCount() {
        return onlinePlayerIds.size();
    }

    private void registerPlayerActor(long playerId) {
        ActorId actorId = ActorId.player(playerId);
        boolean createdActor = !hasActor(actorId);
        if (createdActor) {
            registerActor(actorId, MailBoxType.ORDERED);
        }
        ActorAddress actorAddress = getActorAddress(actorId);
        CallPoint locationService = node.getAnyCallPointByType(ServiceType.LOC);
        if (locationService == null) {
            throw new IllegalStateException("找不到 LocationService 服务路由");
        }
        RpcResult<Void> addResult = LocationServiceProxy.callAdd(locationService, actorId, actorAddress);
        if (!addResult.isSuccess()) {
            if (createdActor) {
                unregisterActor(actorId);
            }
            throw new IllegalStateException("PlayerService 注册 Location 映射失败: playerId=" + playerId
                    + ", errorCode=" + addResult.getErrorCode()
                    + ", message=" + addResult.getErrorMessage());
        }
        getMessageLocationSender().cache(actorId, actorAddress);
    }

    private void removePlayerActor(long playerId) {
        ActorId actorId = ActorId.player(playerId);
        CallPoint locationService = node.getAnyCallPointByType(ServiceType.LOC);
        if (locationService != null) {
            RpcResult<Void> removeResult = LocationServiceProxy.callRemove(locationService, actorId);
            if (!removeResult.isSuccess()) {
                throw new IllegalStateException("PlayerService 删除 Location 映射失败: playerId=" + playerId
                        + ", errorCode=" + removeResult.getErrorCode()
                        + ", message=" + removeResult.getErrorMessage());
            }
        }
        if (hasActor(actorId)) {
            unregisterActor(actorId);
        }
        getMessageLocationSender().remove(actorId);
    }

    @Override
    protected boolean supportMdb() {
        return true;
    }
}
