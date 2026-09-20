package org.evd.game.ConnService.disconnect;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

/** ConnService 关联服务断开后的本地收敛逻辑。 */
@Actor
public final class ConnServiceDisconnectLogic {
    /** 按断开的对端类型分别执行 ConnService 本地收敛。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service == null || service.getServiceType() == null) {
                continue;
            }
            switch (service.getServiceType()) {
                case ONLINE -> onOnlineServiceDisconnect(service);
                case PLAYER -> onPlayerServiceDisconnect(service);
                default -> {
                }
            }
        }
    }

    /** OnlineService 断开时关闭当前 GW 上的全部客户端连接。 */
    private void onOnlineServiceDisconnect(RegisteredService service) {
        ConnService owner = owner();
        int closed = 0;
        LogCore.core.info("ConnService 开始处理 OnlineService 断开: service={}, disconnectedService={}",
                owner.getId(), service.getCallPoint());
        for (NetChannel channel : owner.clientConnection().channelManager().getChannelMap().values()) {
            owner.closeSession(channel, BrokenType.SERVER_KICK.getCode(), "OnlineService 断开连接");
            closed++;
        }
        LogCore.core.info("ConnService 完成 OnlineService 断开处理: service={}, disconnectedService={}, closed={}",
                owner.getId(), service.getCallPoint(), closed);
    }

    /** PlayerService 断开时关闭属于该 PlayerService 的客户端连接。 */
    private void onPlayerServiceDisconnect(RegisteredService service) {
        ConnService owner = owner();
        int closed = 0;
        LogCore.core.info("ConnService 开始处理 PlayerService 断开: service={}, disconnectedService={}",
                owner.getId(), service.getCallPoint());
        for (NetChannel channel : owner.clientConnection().channelManager().getChannelMap().values()) {
            long playerId = channel.getPlayerId();
            if (playerId <= 0L) {
                continue;
            }
            ActorAddress playerAddress = owner.getMessageLocationSender().get(ActorId.player(playerId));
            if (playerAddress == null || !service.getCallPoint().equals(playerAddress.getCallPoint())) {
                continue;
            }
            owner.closeSession(channel, BrokenType.SERVER_KICK.getCode(), "PlayerService 断开连接");
            closed++;
        }
        LogCore.core.info("ConnService 完成 PlayerService 断开处理: service={}, disconnectedService={}, closed={}",
                owner.getId(), service.getCallPoint(), closed);
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
