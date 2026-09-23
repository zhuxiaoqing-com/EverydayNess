package org.evd.game.ConnService.disconnect;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.LocationService.LocationServiceConnectRpcProxy;
import org.evd.game.common.serializeBean.LocationService.SLocationAddress;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** ConnService 关联服务连接后的初始化数据同步逻辑。 */
@Actor
public final class ConnServiceConnectLogic {
    /** 新 LocationService 被发现后，立即发送当前 ConnService 持有的 Gate 地址快照。 */
    public void onServiceConnect(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service != null && service.getServiceType() == ServiceType.LOC) {
                restoreLocationAddresses(service);
            }
        }
    }

    /** Location 重连后只向该实例恢复当前 ConnService 自己持有的网关地址。 */
    private void restoreLocationAddresses(RegisteredService locationService) {
        ConnService owner = owner();
        List<SLocationAddress> addresses = new ArrayList<>();
        for (NetChannel channel : owner.clientConnection().channelManager().getChannelMap().values()) {
            long playerId = channel.getPlayerId();
            if (playerId <= 0L) {
                continue;
            }
            ActorId actorId = ActorId.gate(playerId);
            ActorAddress actorAddress = owner.getGateActorAddress(playerId);
            if (actorAddress == null) {
                continue;
            }
            addresses.add(new SLocationAddress(actorId, actorAddress));
        }
        if (addresses.isEmpty()) {
            LogCore.core.info("ConnService 无须重新发送 GW ActorAddress: service={}, locationService={}",
                    owner.getId(), locationService.getCallPoint());
            return;
        }
        for (SLocationAddress address : addresses) {
            LogCore.core.info("ConnService 批量发送 GW ActorAddress: service={}, locationService={}, actorId={}, actorAddress={}",
                    owner.getId(), locationService.getCallPoint(), address.getActorId(), address.getActorAddress());
        }
        var result = LocationServiceConnectRpcProxy.sendAddBatch(locationService.getCallPoint(), CallFactory.buildServiceInitDataSync(locationService), addresses);
        if (!result.isSuccess()) {
            LogCore.core.warn("ConnService 批量重新发送 GW ActorAddress 失败: service={}, locationService={}, requested={}, errorCode={}, message={}",
                    owner.getId(), locationService.getCallPoint(), addresses.size(),
                    result.getErrorCode(), result.getErrorMessage());
            return;
        }
        LogCore.core.info("ConnService 批量重新发送 GW ActorAddress 完成: service={}, locationService={}, requested={}",
                owner.getId(), locationService.getCallPoint(), addresses.size());
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
