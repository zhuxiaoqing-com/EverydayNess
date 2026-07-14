package org.evd.game.AdminService.controller;

import org.evd.game.AdminService.http.HttpRequest;
import org.evd.game.AdminService.http.HttpRoute;
import org.evd.game.AdminService.http.RequestType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author zhuxiaoqing
 * @Description: RoleController
 * @Date 2026/7/9 19:31
 **/
public class RoleController {

    @HttpRoute(value = "/api/ping", type = RequestType.GET)
    public Map<String, Object> ping(HttpRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uri", request.getUri());
        data.put("clientAddr", request.getClientAddr());
        data.put("path", request.getPath());
        data.put("method", request.getMethod());
        data.put("roleId", request.getInt("roleId"));
        data.put("name", request.getString("name"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 200);
        result.put("message", "ok");
        result.put("data", data);
        return result;
    }

    @HttpRoute(value = "/api/guild", type = RequestType.GET)
    public Map<String, Object> guild(HttpRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uri", request.getUri());
        data.put("clientAddr", request.getClientAddr());
        data.put("roleId", request.getLong("roleId"));
        data.put("name", request.getString("name"));
        data.put("params", request.getParams());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 200);
        result.put("message", "guild ok");
        result.put("data", data);
        return result;
    }
}
