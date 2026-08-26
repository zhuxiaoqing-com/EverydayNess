package org.evd.game.AdminService;

import org.evd.game.AdminService.http.AdminHttpServer;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.*;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

public class AdminService extends Service {
    private volatile AdminHttpServer httpServer;

    public AdminService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    public void init() {
        super.init();
        int port = getServiceInfo().getAddressInfo().getPort();
        this.httpServer = new AdminHttpServer(this, port);
        LogCore.core.info("AdminService HTTP 启动完成: service={}, port={}", id, port);
    }

    @Override
    public void onClose() {
        AdminHttpServer server = httpServer;
        httpServer = null;
        if (server != null) {
            server.shutdown();
        }
        super.onClose();
    }

    /**
     * 远程服务器
     */
    public RpcResult<CallServiceStopResult> callRemoteRpcServiceStop(CallPoint to, long timeoutMill) {
        CallServiceStop callServiceStop = CallFactory.buildCallServiceStop(this, to);
        return RpcResult.call(() -> (CallServiceStopResult) callWait(callServiceStop, timeoutMill));
    }
}
