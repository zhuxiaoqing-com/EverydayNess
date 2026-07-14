package org.evd.game.AdminService.controller;

import org.evd.game.AdminService.http.*;
import org.evd.game.runtime.support.LogCore;

/**
 * 角色管理 HTTP 接口。
 */
@HttpRoute("/server/")
public class ServerController {

    @HttpRoute(value = "/stop", type = RequestType.GET)
    public HttpResult<Void> serverStop(HttpRequest ctx) {

    }
}
