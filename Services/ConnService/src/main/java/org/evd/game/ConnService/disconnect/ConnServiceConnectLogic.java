package org.evd.game.ConnService.disconnect;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.LocationService.LocationServiceRpcProxy;
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

/** ConnService 关联服务连接就绪后的本地恢复逻辑。 */
@Actor
public final class ConnServiceConnectLogic {
    /**
     * 只能在 Service 进入正式路由后恢复 Location 地址。
     *
     * <p>不能把这段逻辑放到 {@code onServiceConnect}：该回调只表示发现了新 Service，
     * 此时目标 Service 仍处于 Pending 状态，还没有加入 Node 的正式服务路由。
     * 这里必须使用回调中明确的 LocationService CallPoint；如果使用 {@code null}，
     * 多个 LocationService 实例存在时可能把地址发给任意实例。</p>
     *
     * <p>{@code onServiceConnectReady} 调用前，Node 已结束 Pending 等待并重建正式路由，
     * 所以这里才满足恢复 RPC 的路由前提，能够避免因服务尚未 Ready 导致当前 GW 持有的
     * 当前 ConnService 自己持有的 gate 地址漏发。地址最终是否成功发出，仍以发送结果和
     * 本地 Actor 是否存在为准。</p>
     */
    public void onServiceConnectReady(Collection<RegisteredService> serviceList) {
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
        var result = LocationServiceRpcProxy.sendAddBatch(locationService.getCallPoint(), CallFactory.buildServiceInitDataSync(locationService), addresses);
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
