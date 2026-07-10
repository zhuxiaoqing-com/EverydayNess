package org.evd.game.AdminService;

import org.evd.game.AdminService.http.AdminHttpServer;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;
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
        this.httpServer = new AdminHttpServer(port);
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
}
