package org.evd.game.PlayerService.disconnect;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.LocationService.LocationServiceConnectRpcProxy;
import org.evd.game.common.proxy.OnlineService.OnlineServiceConnectRpcProxy;
import org.evd.game.common.serializeBean.LocationService.SLocationAddress;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** PlayerService 关联服务连接后的初始化数据同步逻辑。 */
@Actor
public final class PlayerServiceConnectLogic {

    public void onServiceConnect(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service != null && service.getServiceType() == ServiceType.ONLINE) {
                // 在线玩家历史绑定必须早于新 PlayerService 的负载分配。
                restoreOnlineHistoricalBindings(service);
            }
        }
        for (RegisteredService service : serviceList) {
            if (service != null && service.getServiceType() == ServiceType.LOC) {
                restoreLocationAddresses(service);
            }
        }
    }



    /** Location 重连后只向该实例恢复当前 PlayerService 自己持有的玩家地址。 */
    private void restoreLocationAddresses(RegisteredService locationService) {
        PlayerService owner = owner();
        List<SLocationAddress> addresses = new ArrayList<>();
        for (PPlayerOnline online : new ArrayList<>(owner.sessionManager().onlinePlayers())) {
            ActorAddress actorAddress = online.getActorAddress();
            if (actorAddress != null) {
                addresses.add(new SLocationAddress(ActorId.player(online.getPlayerId()), actorAddress));
            }
        }
        if (addresses.isEmpty()) {
            LogCore.core.info("PlayerService 无须重新发送 Player ActorAddress: service={}, locationService={}",
                    owner.getId(), locationService.getCallPoint());
            return;
        }
        for (SLocationAddress address : addresses) {
            LogCore.core.info("PlayerService 批量发送 Player ActorAddress: service={}, locationService={}, actorId={}, actorAddress={}",
                    owner.getId(), locationService.getCallPoint(), address.getActorId(), address.getActorAddress());
        }
        RpcResult<Void> result = LocationServiceConnectRpcProxy.sendAddBatch(locationService.getCallPoint(), CallFactory.buildServiceInitDataSync(locationService), addresses);
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 批量重新发送 Player ActorAddress 失败: service={}, locationService={}, requested={}, errorCode={}, message={}",
                    owner.getId(), locationService.getCallPoint(), addresses.size(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }
        LogCore.core.info("PlayerService 批量重新发送 Player ActorAddress 完成: service={}, locationService={}, requested={}",
                owner.getId(), locationService.getCallPoint(), addresses.size());
    }

    /** Online 重连后，将当前 MDB 中仍保留的玩家历史绑定主动发送给该实例。 */
    private void restoreOnlineHistoricalBindings(RegisteredService onlineService) {
        PlayerService owner = owner();
        List<String> userIds = owner.getMdbPlayerUserIds();
        if (userIds.isEmpty()) {
            LogCore.core.info("PlayerService 无须恢复 Online 历史绑定: service={}, onlineService={}",
                    owner.getId(), onlineService.getCallPoint());
            return;
        }
        for (String userId : userIds) {
            LogCore.core.info("PlayerService 发送 Online 历史绑定: service={}, onlineService={}, userId={}",
                    owner.getId(), onlineService.getCallPoint(), userId);
        }
        RpcResult<Void> result = OnlineServiceConnectRpcProxy.sendRestoreHistoricalPlayerServices(
                onlineService.getCallPoint(),CallFactory.buildServiceInitDataSync(onlineService), userIds, owner.getCallPoint());
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 发送 Online 历史绑定恢复失败: service={}, onlineService={}, count={}, errorCode={}, message={}",
                    owner.getId(), onlineService.getCallPoint(), userIds.size(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }
        LogCore.core.info("PlayerService 发送 Online 历史绑定恢复完成: service={}, onlineService={}, count={}",
                owner.getId(), onlineService.getCallPoint(), userIds.size());
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }

}
