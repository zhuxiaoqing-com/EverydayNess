package org.evd.game.AdminService.controller;

import org.evd.game.AdminService.AdminService;
import org.evd.game.AdminService.http.*;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;

import java.util.concurrent.CompletionStage;

/**
 * 角色管理 HTTP 接口。
 */
@HttpRoute("/server/")
public class ServerController {

    @HttpRoute(value = "/stop", type = RequestType.GET)
    public CompletionStage<HttpResult<Void>> serverStop(HttpRequest ctx) {
        AdminService adminService = ctx.getService(AdminService.class);

        //adminService
    }
}
