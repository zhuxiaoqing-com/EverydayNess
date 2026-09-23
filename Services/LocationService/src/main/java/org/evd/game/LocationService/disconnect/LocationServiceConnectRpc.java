package org.evd.game.LocationService.disconnect;

import org.evd.game.LocationService.LocationService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.LocationService.SLocationAddress;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallServiceInitDataSync;

import java.util.List;

/** LocationService 关联服务连接恢复 RPC 入口。 */
@Actor
@RpcHandler
public final class LocationServiceConnectRpc {
    @Rpc
    public void addBatch(CallServiceInitDataSync syncData, List<SLocationAddress> addresses) {
        if(!Service.checkSyncValid(syncData, "addBatch")) {
            return;
        }
        Service.getCurrent(LocationService.class)
                .getActor(LocationServiceConnectLogic.class)
                .addBatch(addresses);
    }
}
