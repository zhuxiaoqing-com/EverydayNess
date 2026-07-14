package org.evd.game.AdminService.controller;

import org.evd.game.AdminService.http.HttpRequest;
import org.evd.game.AdminService.http.HttpResult;
import org.evd.game.AdminService.http.HttpRoute;
import org.evd.game.AdminService.http.RequestType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 参数绑定示例。
 *
 * <p>每个路由都明确声明了 HTTP 请求类型，服务端会校验 HTTP 方法和
 * Content-Type。</p>
 */
@HttpRoute("/example/http-binding")
public class HttpBindingExampleController {

    /**
     * 多个基础类型：GET /example/http-binding/scalars?playerId=10001&serverId=2
     */
    @HttpRoute(value = "/scalars", type = RequestType.GET)
    public HttpResult<Map<String, Object>> scalars(long playerId, int serverId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("playerId", playerId);
        data.put("serverId", serverId);
        return HttpResult.ok(data);
    }

    /**
     * 基础类型 List：GET /example/http-binding/list?itemIds=11&itemIds=12
     */
    @HttpRoute(value = "/list", type = RequestType.GET)
    public HttpResult<List<Long>> list(List<Long> itemIds) {
        return HttpResult.ok(itemIds);
    }

    /**
     * POST application/x-www-form-urlencoded：
     * playerId=10001&name=test
     */
    @HttpRoute(value = "/form-scalars", type = RequestType.POST_FORM)
    public HttpResult<Map<String, Object>> formScalars(HttpRequest ctx, long playerId, String name) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("playerId", playerId);
        data.put("name", name);
        return HttpResult.ok(data);
    }

    /**
     * 表单或 GET 的全部字段映射到 Map<String, String>，重复字段取第一个值。
     */
    @HttpRoute(value = "/map", type = RequestType.GET)
    public HttpResult<Map<String, String>> map(Map<String, String> parameters) {
        return HttpResult.ok(parameters);
    }

    /**
     * 表单或 GET 的全部字段映射到 Map<String, List<String>>，保留重复值。
     */
    @HttpRoute(value = "/multi-map", type = RequestType.POST_FORM)
    public HttpResult<Map<String, List<String>>> multiMap(
            Map<String, List<String>> parameters) {
        return HttpResult.ok(parameters);
    }

    /**
     * POST application/json：{"playerId":10001,"name":"test","level":20}
     */
    @HttpRoute(value = "/json-object", type = RequestType.POST_JSON)
    public HttpResult<PlayerRequest> jsonObject(PlayerRequest request) {
        return HttpResult.ok(request);
    }

    public record PlayerRequest(Long playerId, String name, Integer level) {
    }
}
