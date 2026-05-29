package org.evd.game.ConnService;

import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.ClientCmdRouteTable;

/**
 * 根据ConnService生成的客户端协议路由注册类
 */
public final class ConnServiceClientCmdRouteRegistry {
    private ConnServiceClientCmdRouteRegistry() {
    }

    public static void register(ClientCmdRouteTable routeTable) {
        routeTable.register(MsgId.C2S_CONN_PING_VALUE, "org.evd.game.ConnService.ConnService", "org.evd.game.common.proxy.ConnServiceProxy");
    }
}
