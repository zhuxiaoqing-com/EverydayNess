package org.evd.game.SceneManagerService.disconnect;

import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallServiceInitDataSync;

import java.util.List;

/** Stage 主动推送地图快照的连接恢复 RPC 入口。 */
@Actor
@RpcHandler
public final class SceneManagerServiceConnectRpc {
    @Rpc
    public void restoreStageMaps(CallServiceInitDataSync syncData, CallPoint stage, List<SMapInfo> maps) {
        if (!Service.checkSyncDataValid(syncData, "restoreStageMaps")) {
            return;
        }
        Service.getCurrent(SceneManagerService.class)
                .getActor(SceneManagerServiceConnectLogic.class)
                .restoreStageMaps(stage, maps);
    }
}
