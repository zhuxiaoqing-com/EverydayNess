package org.evd.game.StageService;

import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.client.ClientCmdRouteTable;

/**
 * 根据StageService生成的客户端协议路由注册类
 */
public final class StageServiceClientCmdRouteRegistry {
    private StageServiceClientCmdRouteRegistry() {
    }

    public static void register(ClientCmdRouteTable routeTable) {
        routeTable.register(MsgId.C2S_LOGIN_VALUE, "org.evd.game.StageService.StageService", "org.evd.game.common.proxy.StageServiceProxy");
    }
}
