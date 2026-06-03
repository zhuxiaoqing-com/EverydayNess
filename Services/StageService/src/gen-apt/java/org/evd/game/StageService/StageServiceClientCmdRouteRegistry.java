package org.evd.game.StageService;

import org.evd.game.runtime.client.ClientCmdRouteTable;

/**
 * 根据StageService生成的客户端协议路由注册类
 */
public final class StageServiceClientCmdRouteRegistry {
    private StageServiceClientCmdRouteRegistry() {
    }

    public static void register(ClientCmdRouteTable routeTable) {
        routeTable.register(1001, "org.evd.game.StageService.StageService");
        routeTable.register(1004, "org.evd.game.StageService.StageService");
    }
}
