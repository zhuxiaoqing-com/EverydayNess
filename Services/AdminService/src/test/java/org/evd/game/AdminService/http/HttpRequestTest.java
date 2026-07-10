package org.evd.game.AdminService.http;

import com.alibaba.fastjson2.JSONObject;
import org.evd.game.AdminService.controller.RoleAdminController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestTest {

    @Test
    void toBean_shouldBindRequestParamsToRecord() {
        HttpRequest request = newHttpRequest(Map.of(
                "roleId", 10001L,
                "minutes", 30,
                "reason", "spam"
        ));

        RoleAdminController.BanRoleRequest body = request.toBean(RoleAdminController.BanRoleRequest.class);

        assertEquals(10001L, body.roleId());
        assertEquals(30, body.minutes());
        assertEquals("spam", body.reason());
    }

    @Test
    void toBean_shouldBindRequestParamsToQueryRecord() {
        HttpRequest request = newHttpRequest(Map.of("roleId", 9527L));

        RoleAdminController.QueryRoleRequest body = request.toBean(RoleAdminController.QueryRoleRequest.class);

        assertEquals(9527L, body.roleId());
    }

    @Test
    void toBean_shouldPreferJsonBodyOverQueryParams() {
        HttpRequest request = newHttpRequest(
                Map.of("roleId", 9527L),
                JSONObject.of("roleId", 10001L, "minutes", 60, "reason", "body")
        );

        RoleAdminController.BanRoleRequest body = request.toBean(RoleAdminController.BanRoleRequest.class);

        assertEquals(10001L, body.roleId());
        assertEquals(60, body.minutes());
        assertEquals("body", body.reason());
    }

    private static HttpRequest newHttpRequest(Map<String, Object> params) {
        return newHttpRequest(params, null);
    }

    private static HttpRequest newHttpRequest(Map<String, Object> params, JSONObject jsonBody) {
        try {
            Constructor<HttpRequest> constructor = HttpRequest.class.getDeclaredConstructor(
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    JSONObject.class,
                    Map.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    "POST",
                    "/admin/role/test",
                    "/admin/role/test",
                    "127.0.0.1:8080",
                    "",
                    jsonBody,
                    new LinkedHashMap<>(params)
            );
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("构造 HttpRequest 失败", e);
        }
    }
}
