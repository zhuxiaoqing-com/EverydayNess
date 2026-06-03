package org.evd.game.ConnService;

import org.evd.game.runtime.client.ClientCmdRouteTable;

/**
 * 根据ConnService生成的客户端协议路由注册类
 */
public final class ConnServiceClientCmdRouteRegistry {
    private ConnServiceClientCmdRouteRegistry() {
    }

    public static void register(ClientCmdRouteTable routeTable) {
        routeTable.register(1002, "org.evd.game.ConnService.ConnService");
    }
}
